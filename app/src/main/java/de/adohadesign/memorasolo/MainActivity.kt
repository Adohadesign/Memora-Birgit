package de.adohadesign.memorasolo

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import android.graphics.drawable.Icon
import android.media.MediaMetadataRetriever
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import de.adohadesign.memorasolo.backup.ArchiveManager
import de.adohadesign.memorasolo.data.*
import de.adohadesign.memorasolo.security.PatternSecurity
import de.adohadesign.memorasolo.voice.OfflineDictation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Gold = Color(0xFFC5A66D)
private val Back = Color(0xFF101218)
private val CardBack = Color(0xFF1C2029)
private val White = Color(0xFFF5F5F7)
private val Muted = Color(0xFFAEB4BF)
private data class MediaTarget(val date: String, val time: String, val existingId: Long?)

private val MemoraColors = darkColorScheme(
    primary = Gold, onPrimary = Back, background = Back, onBackground = White,
    surface = CardBack, onSurface = White, surfaceVariant = Color(0xFF272D37),
    onSurfaceVariant = Muted, outline = Color(0xFF5C6370)
)

class MainActivity : ComponentActivity() {
    private lateinit var repository: DiaryRepository
    private lateinit var security: PatternSecurity
    private lateinit var archive: ArchiveManager
    private var unlocked by mutableStateOf(false)
    private var pendingMode by mutableStateOf("")
    private var pendingBriefstudioId by mutableStateOf<String?>(null)
    private var hasStarted = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = DiaryRepository(this)
        security = PatternSecurity(this)
        archive = ArchiveManager(this, repository)
        pendingMode = intent.getStringExtra("start_mode") ?: ""
        pendingBriefstudioId = intent.getStringExtra("briefstudio_document_id")
        addShortcuts()
        window.statusBarColor = android.graphics.Color.rgb(16, 18, 24)
        window.navigationBarColor = android.graphics.Color.rgb(16, 18, 24)
        setContent {
            MaterialTheme(colorScheme = MemoraColors) {
                Surface(modifier = Modifier.fillMaxSize().safeDrawingPadding(), color = Back) {
                    // Keep activity-result launchers registered while the unlock overlay is shown.
                    Box(Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxSize().then(if (!unlocked) Modifier.alpha(0f).semantics { invisibleToUser() } else Modifier)) {
                            MemoraHome(repository, security, archive, unlocked, pendingMode, pendingBriefstudioId,
                                onShortcutConsumed = { pendingMode = "" },
                                onNotificationConsumed = { pendingBriefstudioId = null })
                        }
                        if (!unlocked) Box(Modifier.fillMaxSize().background(Back)) {
                            AccessScreen(security) { unlocked = true }
                        }
                    }
                }
            }
        }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingMode = intent.getStringExtra("start_mode") ?: ""
    }
    override fun onStart() {
        super.onStart()
        lifecycleScope.launch { repository.foreground() }
        hasStarted = true
    }
    override fun onStop() {
        repository.background()
        unlocked = false // Independent of the 10-minute card session rule.
        super.onStop()
    }
    private fun addShortcuts() {
        if (android.os.Build.VERSION.SDK_INT < 25) return
        val manager = getSystemService(ShortcutManager::class.java)
        val defs = listOf(Triple("TEXT", "Text", "Neuen Text"), Triple("VOICE", "Diktat", "Neues Diktat"), Triple("PHOTO", "Foto", "Foto aufnehmen"))
        val shortcuts = defs.map { (mode, label, full) ->
            ShortcutInfo.Builder(this, "memora_$mode")
                .setShortLabel(label).setLongLabel(full)
                .setIcon(Icon.createWithResource(this, R.mipmap.ic_launcher))
                .setIntent(Intent(this, MainActivity::class.java).apply {
                    action = "de.adohadesign.memorasolo.START_$mode"
                    putExtra("start_mode", mode)
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }).build()
        }
        manager.dynamicShortcuts = shortcuts
    }
}

@Composable
private fun AccessScreen(security: PatternSecurity, onUnlocked: () -> Unit) {
    var step by remember { mutableStateOf(if (security.configured) "unlock" else "newPattern") }
    var firstPattern by remember { mutableStateOf("") }
    var firstPin by remember { mutableStateOf("") }
    var pinText by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var pinMode by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Default.Lock, contentDescription = null, tint = Gold, modifier = Modifier.size(44.dp))
        Spacer(Modifier.height(16.dp))
        Text("MEMORA SOLO", fontSize = 28.sp, fontWeight = FontWeight.SemiBold, color = Gold)
        Spacer(Modifier.height(8.dp))
        when (step) {
            "newPattern", "confirmPattern", "unlock" -> {
                Text(when (step) {
                    "newPattern" -> "Entsperrmuster festlegen (mindestens vier Punkte)"
                    "confirmPattern" -> "Muster wiederholen"
                    else -> if (pinMode) "Notfall-PIN eingeben" else "Entsperrmuster zeichnen"
                }, color = Muted)
                Spacer(Modifier.height(24.dp))
                if (step == "unlock" && pinMode) {
                    OutlinedTextField(pinText, { pinText = it.filter(Char::isDigit).take(4) },
                        label = { Text("Vierstelliger PIN") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = {
                        if (security.verifyPin(pinText)) onUnlocked()
                        else message = "PIN falsch oder vorübergehend gesperrt (${security.delaySeconds()} s)."
                        pinText = ""
                    }, enabled = pinText.length == 4) { Text("Entsperren") }
                } else {
                    PatternPad { pattern ->
                        when (step) {
                            "newPattern" -> {
                                if (pattern.length < 4) message = "Mindestens vier verschiedene Punkte wählen."
                                else { firstPattern = pattern; step = "confirmPattern"; message = "" }
                            }
                            "confirmPattern" -> {
                                if (pattern != firstPattern) { step = "newPattern"; message = "Muster stimmen nicht überein. Erneut festlegen." }
                                else { step = "newPin"; message = "" }
                            }
                            "unlock" -> {
                                if (security.verifyPattern(pattern)) onUnlocked()
                                else message = "Muster falsch oder vorübergehend gesperrt (${security.delaySeconds()} s)."
                            }
                        }
                    }
                }
                if (step == "unlock") TextButton(onClick = { pinMode = !pinMode; message = "" }) {
                    Text(if (pinMode) "Muster verwenden" else "Muster vergessen? Mit PIN entsperren")
                }
            }
            "newPin", "confirmPin" -> {
                Text(if (step == "newPin") "Vierstelligen Notfall-PIN festlegen" else "PIN wiederholen", color = Muted)
                OutlinedTextField(pinText, { pinText = it.filter(Char::isDigit).take(4) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), label = { Text("PIN") })
                Spacer(Modifier.height(12.dp))
                Button(enabled = pinText.length == 4, onClick = {
                    if (step == "newPin") { firstPin = pinText; pinText = ""; step = "confirmPin" }
                    else if (firstPin == pinText) {
                        security.setup(firstPattern, firstPin)
                        pinText = ""; onUnlocked()
                    } else { step = "newPin"; pinText = ""; message = "PINs stimmen nicht überein." }
                }) { Text("Weiter") }
            }
        }
        if (message.isNotEmpty()) Text(message, color = Color(0xFFF2B8B5), modifier = Modifier.padding(14.dp))
    }
}

@Composable
private fun PatternPad(onPattern: (String) -> Unit) {
    var path by remember { mutableStateOf(listOf<Int>()) }
    var gridSize by remember { mutableStateOf(IntSize(250, 250)) }
    Canvas(Modifier.size(264.dp).onSizeChanged { gridSize = it }.pointerInput(Unit) {
        fun indexFor(point: Offset): Int? {
            val unit = gridSize.width / 3f
            val col = (point.x / unit).toInt().coerceIn(0, 2)
            val row = (point.y / unit).toInt().coerceIn(0, 2)
            val center = Offset((col + .5f) * unit, (row + .5f) * unit)
            return if ((point - center).getDistance() <= unit * .46f) row * 3 + col else null
        }
        detectDragGestures(onDragStart = { pos -> path = listOfNotNull(indexFor(pos)) },
            onDragEnd = { val result = path.joinToString(""); path = emptyList(); if (result.isNotBlank()) onPattern(result) },
            onDragCancel = { path = emptyList() }) { change, _ ->
            indexFor(change.position)?.let { if (it !in path) path = path + it }
            change.consume()
        }
    }) {
        val unit = size.width / 3f
        fun center(index: Int) = Offset((index % 3 + .5f) * unit, (index / 3 + .5f) * unit)
        if (path.size > 1) path.zipWithNext().forEach { (a, b) ->
            drawLine(Gold, center(a), center(b), strokeWidth = 9f)
        }
        for (i in 0 until 9) {
            drawCircle(if (i in path) Gold else Muted, radius = if (i in path) 14f else 10f, center = center(i))
            drawCircle(Gold.copy(alpha = .35f), radius = 23f, center = center(i), style = Stroke(width = 1.5f))
        }
    }
}

@Composable
private fun MemoraHome(repo: DiaryRepository, security: PatternSecurity, archive: ArchiveManager,
    unlocked: Boolean, shortcutMode: String, briefstudioNotificationId: String?,
    onShortcutConsumed: () -> Unit, onNotificationConsumed: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedDate by rememberSaveable { mutableStateOf(repo.today()) }
    var showCalendar by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showBriefstudioLogin by remember { mutableStateOf(false) }
    var titlePromptEntry by remember { mutableStateOf<Long?>(null) }
    var titlePromptText by remember { mutableStateOf("") }
    var connectionEstablished by remember { mutableStateOf(BriefstudioTransfer.isConnected(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(connectionEstablished) {
        if (connectionEstablished) {
            BriefstudioNotificationWorker.schedule(context)
            if (android.os.Build.VERSION.SDK_INT >= 33 &&
                androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
    var selectedEntry by rememberSaveable { mutableStateOf<Long?>(null) }
    var editTextId by rememberSaveable { mutableStateOf<Long?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var mediaChoice by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var toDeleteEntry by remember { mutableStateOf<Entry?>(null) }
    var toDeleteElement by remember { mutableStateOf<DiaryElement?>(null) }
    var editDateTime by remember { mutableStateOf<Entry?>(null) }
    var restoreUri by remember { mutableStateOf<Uri?>(null) }
    var captureUriString by rememberSaveable { mutableStateOf<String?>(null) }
    var captureFilePath by rememberSaveable { mutableStateOf<String?>(null) }
    var mediaTargetDate by rememberSaveable { mutableStateOf<String?>(null) }
    var mediaTargetTime by rememberSaveable { mutableStateOf<String?>(null) }
    var mediaTargetEntry by rememberSaveable { mutableStateOf<Long?>(null) }
    val entryFlow = remember(selectedDate) { repo.entries(selectedDate) }
    val entries by entryFlow.collectAsState(initial = emptyList())
    val savedDates by repo.dates().collectAsState(initial = emptyList())
    fun error(t: Throwable) { message = t.message ?: "Unbekannter Fehler" }
    // Only sync after an unlock; offline diary features remain available without internet.
    LaunchedEffect(unlocked) {
        if (unlocked && BriefstudioTransfer.isConnected(context)) {
            try {
                val incoming = BriefstudioTransfer.fetchSaved(context)
                var imported = 0
                for (document in incoming) if (repo.importBriefstudio(document)) {
                    imported++
                    BriefstudioNotificationWorker.notifyImported(context, document)
                }
                if (imported > 0) message = "$imported neue(r) Text(e) aus Briefstudio übernommen. Über den Kalender öffnen."
            } catch (e: Exception) {
                message = "Briefstudio konnte nicht synchronisiert werden: ${e.message ?: "Verbindung prüfen"}"
            }
        }
    }
    LaunchedEffect(unlocked, briefstudioNotificationId) {
        if (unlocked && briefstudioNotificationId != null) {
            // Sync runs separately on unlock. Resolve the stable mapping after it completes,
            // allowing the import to finish before opening the day entry.
            val targetId = briefstudioNotificationId
            repeat(12) {
                val entryId = repo.dao.importedEntryId(targetId)
                if (entryId != null) {
                    val entry = repo.dao.entry(entryId)
                    if (entry != null) {
                        selectedDate = entry.entryDate
                        selectedEntry = entryId
                        showCalendar = false
                        showSettings = false
                        editTextId = null
                        onNotificationConsumed()
                        return@LaunchedEffect
                    }
                }
                kotlinx.coroutines.delay(500)
            }
            onNotificationConsumed()
            message = "Der Eintrag ist noch nicht verfügbar. Bitte Synchronisierung prüfen."
        }
    }
    fun newCaptureUri(video: Boolean = false): Uri {
        val dir = File(context.cacheDir, "capture").also { it.mkdirs() }
        val file = File(dir, "capture_${System.currentTimeMillis()}_${java.util.UUID.randomUUID()}.${if (video) "mp4" else "jpg"}")
        captureFilePath = file.absolutePath
        return FileProvider.getUriForFile(context, "${context.packageName}.provider", file).also { captureUriString = it.toString() }
    }
    fun prepareTarget() {
        val date = selectedDate
        val current = selectedEntry
        mediaTargetDate = date
        mediaTargetTime = repo.timeNow()
        mediaTargetEntry = current
    }
    fun insertMedia(uri: Uri, type: String, cleanup: File? = null) {
        val origin = MediaTarget(mediaTargetDate ?: selectedDate, mediaTargetTime ?: repo.timeNow(), mediaTargetEntry ?: selectedEntry)
        scope.launch {
            busy = true
            try {
                val elementId = repo.addFile(origin.date, uri, type, origin.existingId, origin.time, cameraCapture = type == "PHOTO" && cleanup != null)
                if (origin.existingId == null) {
                    titlePromptEntry = repo.dao.element(elementId)?.entryId
                    titlePromptText = ""
                }
                message = "Datei hinzugefügt"
            }
            catch (e: Exception) { error(e) }
            finally { cleanup?.delete(); busy = false }
        }
    }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = captureUriString?.let(Uri::parse); val file = captureFilePath?.let(::File)
        if (ok && uri != null) insertMedia(uri, "PHOTO", file) else file?.delete()
        captureUriString = null; captureFilePath = null
    }
    val takeVideo = rememberLauncherForActivityResult(ActivityResultContracts.CaptureVideo()) { ok ->
        val uri = captureUriString?.let(Uri::parse); val file = captureFilePath?.let(::File)
        if (ok && uri != null) insertMedia(uri, "VIDEO", file) else file?.delete()
        captureUriString = null; captureFilePath = null
    }
    val pickPhotos = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach { insertMedia(it, "PHOTO") }
    }
    val pickVideos = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach { insertMedia(it, "VIDEO") }
    }
    val pickFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach { insertMedia(it, "FILE") }
    }
    val backup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) scope.launch {
            busy = true
            try { message = archive.backup(uri) } catch (e: Exception) { error(e) } finally { busy = false }
        }
    }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) restoreUri = uri
    }
    fun addText(dictate: Boolean = false) {
        scope.launch {
            try {
                val id = repo.addText(selectedDate, if (dictate) "DICTATION" else "TEXT", selectedEntry)
                if (selectedEntry == null) {
                    titlePromptEntry = repo.dao.element(id)?.entryId
                    titlePromptText = ""
                }
                editTextId = id
                showAdd = false
            } catch (e: Exception) { error(e) }
        }
    }
    fun photo() { prepareTarget(); mediaChoice = "PHOTO"; showAdd = false }
    fun video() { prepareTarget(); mediaChoice = "VIDEO"; showAdd = false }
    LaunchedEffect(shortcutMode, unlocked) {
        if (unlocked && shortcutMode.isNotEmpty()) {
            when (shortcutMode) {
                "TEXT" -> addText()
                "VOICE" -> addText(true)
                "PHOTO" -> { prepareTarget(); takePhoto.launch(newCaptureUri()) }
            }
            onShortcutConsumed()
        }
    }
    Column(Modifier.fillMaxSize().background(Back)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("MEMORA SOLO", color = Gold, fontSize = 12.sp, letterSpacing = 2.sp)
                Text(if (showSettings) "Einstellungen" else if (showCalendar) "Kalender" else if (selectedEntry != null) "Eintrag" else formatDate(selectedDate),
                    fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = White)
            }
            if (showSettings || selectedEntry != null || editTextId != null) {
                IconButton(onClick = {
                    if (editTextId != null) editTextId = null
                    else if (selectedEntry != null) selectedEntry = null
                    else showSettings = false
                }) { Icon(Icons.Default.ArrowBack, "Zurück", tint = Gold) }
            } else IconButton(onClick = { showSettings = true; showCalendar = false }) {
                Icon(Icons.Default.Settings, "Einstellungen", tint = Gold)
            }
        }
        HorizontalDivider(color = CardBack)
        Box(Modifier.weight(1f)) {
            when {
                busy -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Gold)
                        Spacer(Modifier.height(12.dp))
                        Text("Dateien werden verarbeitet …")
                    }
                }
                editTextId != null -> TextEditor(repo, editTextId!!, onBack = { editTextId = null })
                showSettings -> SettingsPage(repo, security,
                    onBackup = { backup.launch("MemoraSolo_${repo.today()}.zip") },
                    onRestore = { restore.launch(arrayOf("application/zip", "application/octet-stream")) },
                    onBriefstudioImport = {
                        if (BriefstudioTransfer.isConnected(context)) {
                            scope.launch {
                                busy = true
                                try {
                                    val incoming = BriefstudioTransfer.fetchSaved(context)
                                    var imported = 0
                                    for (document in incoming) if (repo.importBriefstudio(document)) {
                                        imported++
                                        BriefstudioNotificationWorker.notifyImported(context, document)
                                    }
                                    message = if (imported > 0) "$imported neue Texte übernommen. Im Kalender ansehen."
                                        else "Synchronisiert: Keine neuen Texte."
                                } catch (e: Exception) { error(e) }
                                finally { busy = false }
                            }
                        } else showBriefstudioLogin = true
                    },
                    briefstudioConnected = connectionEstablished)
                showCalendar -> CalendarPage(selectedDate, savedDates.toSet(), onPick = { selectedDate = it; showCalendar = false; selectedEntry = null })
                selectedEntry != null -> EntryPage(repo, selectedEntry!!,
                    onEditText = { editTextId = it },
                    onChangeDateTime = { scope.launch { editDateTime = repo.dao.entry(selectedEntry!!) } },
                    onDelete = { scope.launch { toDeleteEntry = repo.dao.entry(selectedEntry!!) } },
                    onDeleteElement = { toDeleteElement = it },
                    onMessage = { message = it },
                    onClose = { selectedEntry = null },
                    onAdd = { showAdd = true })
                else -> DayPage(entries,
                    onOpen = { selectedEntry = it },
                    onPrevious = { selectedDate = LocalDate.parse(selectedDate).minusDays(1).toString() },
                    onNext = { selectedDate = LocalDate.parse(selectedDate).plusDays(1).toString() })
            }
        }
        NavigationBar(containerColor = CardBack, modifier = Modifier.height(80.dp)) {
            NavigationBarItem(selected = showCalendar, onClick = { showCalendar = true; showSettings = false; selectedEntry = null; editTextId = null },
                icon = { Icon(Icons.Default.CalendarMonth, "Kalender") }, label = { Text("Kalender") })
            NavigationBarItem(selected = false, onClick = { showAdd = true }, icon = {
                Icon(Icons.Default.AddCircle, "Einfügen", modifier = Modifier.size(32.dp), tint = Gold)
            }, label = { Text("Einfügen") })
            NavigationBarItem(selected = false, onClick = {
                selectedDate = repo.today(); selectedEntry = null; editTextId = null; showCalendar = false; showSettings = false
            }, icon = { Icon(Icons.Default.Today, "Jetzt") }, label = { Text("Jetzt") })
        }
    }
    if (titlePromptEntry != null) {
        val target = titlePromptEntry!!
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Überschrift für diesen Eintrag") },
            text = {
                Column {
                    Text("Jeder Eintrag erhält eine Überschrift. Sie erscheint im Kalender und in der Tagesübersicht.")
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = titlePromptText,
                        onValueChange = { titlePromptText = it.take(120) },
                        label = { Text("Überschrift") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        try {
                            repo.renameEntry(target, titlePromptText.trim().ifBlank { "Eintrag vom ${selectedDate}" })
                            titlePromptEntry = null
                        } catch (e: Exception) { error(e) }
                    }
                }) { Text("Speichern") }
            }
        )
    }
    if (showBriefstudioLogin) {
        BriefstudioLoginDialog(onDismiss = { showBriefstudioLogin = false }, onImport = { email, password ->
            showBriefstudioLogin = false
            scope.launch {
                busy = true
                try {
                    val incoming = BriefstudioTransfer.connectAndFetch(context, email, password)
                    var imported = 0
                    var skipped = 0
                    for (document in incoming) {
                        if (repo.importBriefstudio(document)) {
                            imported++
                            BriefstudioNotificationWorker.notifyImported(context, document)
                        } else skipped++
                    }
                    connectionEstablished = true
                    message = "Briefstudio verbunden. $imported übernommen, $skipped bereits vorhanden. Neue Texte werden beim Entsperren automatisch geholt."
                } catch (e: Exception) { error(e) }
                finally { busy = false }
            }
        })
    }
    if (showAdd) AlertDialog(onDismissRequest = { showAdd = false }, title = { Text("Inhalt hinzufügen") }, text = {
        Column {
            AddOption(Icons.Default.Edit, "Text schreiben") { addText() }
            AddOption(Icons.Default.Mic, "Diktat") { addText(true) }
            AddOption(Icons.Default.PhotoCamera, "Foto") { photo() }
            AddOption(Icons.Default.Videocam, "Video") { video() }
            AddOption(Icons.Default.AttachFile, "Datei / PDF") { showAdd = false; prepareTarget(); pickFiles.launch(arrayOf("*/*")) }
        }
    }, confirmButton = { TextButton(onClick = { showAdd = false }) { Text("Schließen") } })
    if (mediaChoice.isNotEmpty()) AlertDialog(onDismissRequest = { mediaChoice = "" },
        title = { Text(if (mediaChoice == "PHOTO") "Foto hinzufügen" else "Video hinzufügen") }, text = {
            Column {
                AddOption(Icons.Default.PhotoCamera, "Direkt aufnehmen") {
                    val choice = mediaChoice; mediaChoice = ""
                    if (choice == "PHOTO") takePhoto.launch(newCaptureUri()) else takeVideo.launch(newCaptureUri(video = true))
                }
                AddOption(Icons.Default.Collections, "Aus Galerie wählen") {
                    val choice = mediaChoice; mediaChoice = ""
                    if (choice == "PHOTO") pickPhotos.launch(arrayOf("image/*")) else pickVideos.launch(arrayOf("video/*"))
                }
            }
        }, confirmButton = { TextButton(onClick = { mediaChoice = "" }) { Text("Abbrechen") } })
    if (restoreUri != null) AlertDialog(onDismissRequest = { restoreUri = null },
        title = { Text("Sicherung wiederherstellen?") }, text = {
            Text("Vorhandene Einträge bleiben bestehen. Die gesicherten Einträge werden hinzugefügt. Bei mehrfacher Wiederherstellung können Duplikate entstehen. Nur eine vertrauenswürdige Sicherung wählen.")
        }, confirmButton = { TextButton(onClick = {
            val uri = restoreUri!!; restoreUri = null
            scope.launch { busy = true; try { message = archive.restore(uri) } catch (e: Exception) { error(e) }; busy = false }
        }) { Text("Hinzufügen") } }, dismissButton = { TextButton(onClick = { restoreUri = null }) { Text("Abbrechen") } })
    toDeleteEntry?.let { entry ->
        AlertDialog(onDismissRequest = { toDeleteEntry = null }, title = { Text("Eintrag endgültig löschen?") },
            text = { Text("${formatDate(entry.entryDate)}, ${entry.displayTime} Uhr. Dieser Eintrag und seine Dateien werden ohne Papierkorb gelöscht.") },
            confirmButton = { TextButton(onClick = {
                scope.launch { repo.deleteEntry(entry.id); selectedEntry = null; toDeleteEntry = null; message = "Eintrag gelöscht" }
            }) { Text("Endgültig löschen") } },
            dismissButton = { TextButton(onClick = { toDeleteEntry = null }) { Text("Abbrechen") } })
    }
    toDeleteElement?.let { element ->
        AlertDialog(onDismissRequest = { toDeleteElement = null }, title = { Text("Element endgültig löschen?") },
            text = { Text("${element.type}: Dieser Inhalt wird ohne Papierkorb gelöscht.") },
            confirmButton = { TextButton(onClick = { scope.launch { repo.deleteElement(element.id); toDeleteElement = null } }) { Text("Löschen") } },
            dismissButton = { TextButton(onClick = { toDeleteElement = null }) { Text("Abbrechen") } })
    }
    editDateTime?.let { entry ->
        EntryTimeDialog(entry, onDismiss = { editDateTime = null }, onSave = { day, time ->
            scope.launch { repo.changeDateTime(entry.id, day, time); selectedDate = day; editDateTime = null }
        })
    }
    if (message.isNotBlank()) AlertDialog(onDismissRequest = { message = "" }, title = { Text("Memora Solo") },
        text = { Text(message) }, confirmButton = { TextButton(onClick = { message = "" }) { Text("OK") } })
}

@Composable private fun AddOption(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, action: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = action).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = Gold)
        Spacer(Modifier.width(14.dp))
        Text(label, fontSize = 16.sp)
    }
}
private fun formatDate(day: String): String = try {
    LocalDate.parse(day).format(DateTimeFormatter.ofPattern("EEEE, d. MMMM yyyy", Locale.GERMAN))
} catch (_: Exception) { day }

@Composable
private fun DayPage(entries: List<Entry>, onOpen: (Long) -> Unit, onPrevious: () -> Unit, onNext: () -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPrevious) { Icon(Icons.Default.ChevronLeft, "Vortag", tint = Gold) }
            Text("${entries.size} Einträge", color = Muted)
            IconButton(onClick = onNext) { Icon(Icons.Default.ChevronRight, "Folgetag", tint = Gold) }
        }
        if (entries.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.MenuBook, null, tint = Gold, modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(12.dp))
                Text("Noch keine Einträge", color = Muted)
                Text("Mit + einen Eintrag beginnen", color = Muted)
            }
        } else LazyColumn(contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(entries, key = { it.id }) { entry -> EntryCard(entry, onOpen) }
        }
    }
}

@Composable
private fun EntryCard(entry: Entry, onOpen: (Long) -> Unit) {
    val db = DiaryDatabase.get(LocalContext.current)
    val elements by remember(entry.id) { db.diaryDao().watchElements(entry.id) }.collectAsState(initial = emptyList())
    val preview = elements.firstOrNull { it.text.isNotBlank() }?.text ?: "Foto, Video oder Datei"
    val photos = elements.count { it.type == "PHOTO" }
    val videos = elements.count { it.type == "VIDEO" }
    val files = elements.count { it.type == "FILE" }
    Card(onClick = { onOpen(entry.id) }, shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = CardBack), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${entry.displayTime} Uhr", color = Gold, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                if (entry.isOpen) Text("OFFEN", color = Muted, fontSize = 10.sp)
                Spacer(Modifier.width(7.dp))
                Icon(Icons.Default.ChevronRight, "Öffnen", tint = Muted)
            }
            Text(entry.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text(preview, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 13.sp, color = Muted)
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                if (elements.any { it.type == "TEXT" || it.type == "DICTATION" }) CaptionIcon(Icons.Default.Notes, "Text")
                if (photos > 0) CaptionIcon(Icons.Default.Photo, "$photos Fotos")
                if (videos > 0) CaptionIcon(Icons.Default.VideoFile, "$videos Videos")
                if (files > 0) CaptionIcon(Icons.Default.AttachFile, "$files Dateien")
            }
        }
    }
}

@Composable
private fun CaptionIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Gold, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, color = Muted, fontSize = 11.sp)
    }
}

@Composable
private fun CalendarPage(date: String, markedDates: Set<String>, onPick: (String) -> Unit) {
    var month by remember(date) { mutableStateOf(YearMonth.from(LocalDate.parse(date))) }
    val firstWeekday = month.atDay(1).dayOfWeek.value - 1
    val total = month.lengthOfMonth()
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            IconButton(onClick = { month = month.minusMonths(1) }) { Icon(Icons.Default.ChevronLeft, "Vormonat", tint = Gold) }
            Text(month.atDay(1).format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.GERMAN)),
                color = White, fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
            IconButton(onClick = { month = month.plusMonths(1) }) { Icon(Icons.Default.ChevronRight, "Folgemonat", tint = Gold) }
        }
        Spacer(Modifier.height(12.dp))
        Row {
            for (weekday in listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So"))
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { Text(weekday, color = Muted, fontSize = 13.sp) }
        }
        Spacer(Modifier.height(12.dp))
        repeat((firstWeekday + total + 6) / 7) { week ->
            Row(Modifier.fillMaxWidth()) {
                for (col in 0 until 7) {
                    val day = week * 7 + col - firstWeekday + 1
                    if (day !in 1..total) Box(Modifier.weight(1f).height(58.dp))
                    else {
                        val value = month.atDay(day).toString()
                        val selected = value == date
                        Box(Modifier.weight(1f).padding(2.dp).height(54.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selected) Gold else CardBack)
                            .clickable { onPick(value) }, contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("$day", color = if (selected) Back else White, fontWeight = FontWeight.Medium)
                                if (value in markedDates) Box(Modifier.padding(top = 4.dp).size(5.dp)
                                    .clip(RoundedCornerShape(3.dp)).background(if (selected) Back else Gold))
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        val context = LocalContext.current
        OutlinedButton(onClick = {
            val d = month.atDay(1)
            DatePickerDialog(context, { _, y, m, day -> onPick(LocalDate.of(y, m + 1, day).toString()) },
                d.year, d.monthValue - 1, d.dayOfMonth).show()
        }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Event, null); Spacer(Modifier.width(8.dp)); Text("Anderes Jahr / Datum auswählen") }
    }
}

@Composable
private fun EntryPage(repo: DiaryRepository, entryId: Long,
    onEditText: (Long) -> Unit, onChangeDateTime: () -> Unit,
    onDelete: () -> Unit, onDeleteElement: (DiaryElement) -> Unit,
    onMessage: (String) -> Unit, onClose: () -> Unit, onAdd: () -> Unit) {
    val dao = repo.dao
    val elements by remember(entryId) { dao.watchElements(entryId) }.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var entry by remember(entryId) { mutableStateOf<Entry?>(null) }
    var chooseMerge by remember { mutableStateOf(false) }
    var renameTitle by remember { mutableStateOf(false) }
    var editedTitle by remember { mutableStateOf("") }
    var choosingMove by remember { mutableStateOf<DiaryElement?>(null) }
    LaunchedEffect(entryId, elements) { entry = dao.entry(entryId) }
    Column {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(entry?.let { "${formatDate(it.entryDate)}, ${it.displayTime} Uhr" } ?: "Eintrag", modifier = Modifier.weight(1f), color = Gold)
            IconButton(onClick = { editedTitle = entry?.title.orEmpty(); renameTitle = true }) { Icon(Icons.Default.Title, "Überschrift bearbeiten", tint = Gold) }
            IconButton(onClick = onChangeDateTime) { Icon(Icons.Default.EditCalendar, "Datum und Uhrzeit", tint = Gold) }
            IconButton(onClick = { chooseMerge = true }) { Icon(Icons.Default.MergeType, "Einträge zusammenführen", tint = Muted) }
            IconButton(onClick = onDelete) { Icon(Icons.Default.DeleteForever, "Eintrag löschen", tint = Color(0xFFF2B8B5)) }
        }
        Text(entry?.title ?: "Eintrag", modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp), fontSize = 22.sp, fontWeight = FontWeight.Bold)
        LazyColumn(contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(elements, key = { it.id }) { element ->
                Column {
                    ElementCard(repo, element, onEditText, onDeleteElement,
                        onSplit = { scope.launch { val id = repo.splitAt(element.id); if (id > 0) onMessage("Eintrag geteilt.") } },
                        onMove = { choosingMove = element }, onMessage = onMessage)
                }
            }
            item {
                OutlinedButton(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("Weiteren Inhalt hinzufügen")
                }
            }
        }
    }
    if (renameTitle) {
        AlertDialog(onDismissRequest = { renameTitle = false },
            title = { Text("Überschrift bearbeiten") },
            text = { OutlinedTextField(value = editedTitle, onValueChange = { editedTitle = it.take(120) },
                label = { Text("Überschrift") }, singleLine = true) },
            confirmButton = { TextButton(onClick = {
                scope.launch {
                    try { repo.renameEntry(entryId, editedTitle); entry = dao.entry(entryId); renameTitle = false }
                    catch (e: Exception) { onMessage(e.message ?: "Überschrift konnte nicht gespeichert werden") }
                }
            }) { Text("Speichern") } },
            dismissButton = { TextButton(onClick = { renameTitle = false }) { Text("Abbrechen") } })
    }
    if (chooseMerge) {
        val all by remember(entry?.entryDate) { repo.entries(entry?.entryDate ?: repo.today()) }.collectAsState(initial = emptyList())
        AlertDialog(onDismissRequest = { chooseMerge = false }, title = { Text("Einträge zusammenführen") },
            text = { Column {
                Text("Zweiten Eintrag auswählen. Dessen Inhalte werden in diesen Eintrag verschoben.")
                all.filter { it.id != entryId }.forEach { candidate ->
                    TextButton(onClick = {
                        scope.launch { repo.mergeEntries(entryId, candidate.id); chooseMerge = false; onMessage("Einträge zusammengeführt") }
                    }) { Text("${candidate.displayTime} Uhr") }
                }
            } }, confirmButton = { TextButton(onClick = { chooseMerge = false }) { Text("Schließen") } })
    }
    choosingMove?.let { element ->
        val context = LocalContext.current
        AlertDialog(onDismissRequest = { choosingMove = null }, title = { Text("Element verschieben") },
            text = { Text("Datum wählen. Das Element wird einem neuen Eintrag am ausgewählten Tag zugeordnet.") },
            confirmButton = { TextButton(onClick = {
                choosingMove = null
                val d = LocalDate.parse(entry?.entryDate ?: repo.today())
                DatePickerDialog(context, { _, y, m, day ->
                    scope.launch {
                        val id = repo.createNew(LocalDate.of(y, m + 1, day).toString())
                        repo.moveElement(element.id, id)
                        onMessage("Element verschoben")
                    }
                }, d.year, d.monthValue - 1, d.dayOfMonth).show()
            }) { Text("Datum wählen") } }, dismissButton = { TextButton(onClick = { choosingMove = null }) { Text("Abbrechen") } })
    }
}

@Composable
private fun ElementCard(repo: DiaryRepository, element: DiaryElement, onEditText: (Long) -> Unit,
    onDeleteElement: (DiaryElement) -> Unit, onSplit: () -> Unit, onMove: () -> Unit, onMessage: (String) -> Unit) {
    val context = LocalContext.current
    val asset by remember(element.id) { repo.dao.watchAsset(element.id) }.collectAsState(initial = null)
    var actions by remember(element.id) { mutableStateOf(false) }
    var showPdf by remember(element.id) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = CardBack), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(when (element.type) { "DICTATION" -> "Diktierter Text"; "PHOTO" -> "Foto"; "VIDEO" -> "Video"; "FILE" -> "Datei"; else -> "Text" },
                    color = Gold, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                if (element.type in listOf("TEXT", "DICTATION")) IconButton(onClick = { onEditText(element.id) }) {
                    Icon(Icons.Default.Edit, "Text bearbeiten", tint = Gold)
                }
                Box {
                    IconButton(onClick = { actions = true }) { Icon(Icons.Default.MoreVert, "Weitere Aktionen", tint = Muted) }
                    DropdownMenu(expanded = actions, onDismissRequest = { actions = false }) {
                        DropdownMenuItem(text = { Text("Nach oben") }, onClick = {
                            actions = false; scope.launch { repo.moveElementOrder(element.id, -1) }
                        })
                        DropdownMenuItem(text = { Text("Nach unten") }, onClick = {
                            actions = false; scope.launch { repo.moveElementOrder(element.id, 1) }
                        })
                        DropdownMenuItem(text = { Text("Auf anderen Tag verschieben") }, onClick = { actions = false; onMove() })
                        DropdownMenuItem(text = { Text("Eintrag hier teilen") }, onClick = { actions = false; onSplit() })
                        DropdownMenuItem(text = { Text("Endgültig löschen") }, onClick = { actions = false; onDeleteElement(element) })
                    }
                }
            }
            if (element.type in listOf("TEXT", "DICTATION")) Text(element.text.ifBlank { "Noch kein Text" },
                color = White, modifier = Modifier.fillMaxWidth().clickable { onEditText(element.id) }.padding(4.dp))
            else if (asset != null) {
                val file = repo.file(asset!!)
                if (element.type == "PHOTO" || element.type == "VIDEO") {
                    val preview = remember(file.absolutePath) { thumbnail(file, element.type == "VIDEO") }
                    if (preview != null) Image(preview.asImageBitmap(), contentDescription = asset!!.originalName,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp).clickable {
                            if (asset!!.mimeType == "application/pdf") showPdf = true
                            else openMedia(context, file, asset!!.mimeType, onMessage)
                        }, contentScale = ContentScale.Fit)
                }
                Text(asset!!.originalName, color = Muted, fontSize = 13.sp)
                OutlinedButton(onClick = {
                    if (asset!!.mimeType == "application/pdf") showPdf = true
                    else openMedia(context, file, asset!!.mimeType, onMessage)
                }) {
                    Icon(if (element.type == "VIDEO") Icons.Default.PlayArrow else Icons.Default.OpenInNew, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (element.type == "VIDEO") "Video abspielen" else "Datei öffnen")
                }
            }
        }
    }
    if (showPdf && asset != null) PdfViewer(repo.file(asset!!)) { showPdf = false }
}

@Composable
private fun PdfViewer(file: File, onClose: () -> Unit) {
    var page by remember(file.absolutePath) { mutableIntStateOf(0) }
    var error by remember(file.absolutePath) { mutableStateOf("") }
    val pages = remember(file.absolutePath) {
        runCatching {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                PdfRenderer(fd).use { it.pageCount }
            }
        }.getOrDefault(0)
    }
    val bmp = remember(file.absolutePath, page) {
        runCatching {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                PdfRenderer(fd).use { renderer ->
                    renderer.openPage(page).use { current ->
                        val scale = minOf(1200f / current.width, 1600f / current.height, 2f)
                        val width = (current.width * scale).toInt().coerceAtLeast(1)
                        val height = (current.height * scale).toInt().coerceAtLeast(1)
                        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { image ->
                            image.eraseColor(android.graphics.Color.WHITE)
                            current.render(image, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        }
                    }
                }
            }
        }.getOrNull()
    }
    Dialog(onDismissRequest = onClose) {
        Surface(shape = RoundedCornerShape(16.dp), color = CardBack, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("PDF · Seite ${page + 1}/$pages", color = Gold, modifier = Modifier.weight(1f))
                    IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Schließen") }
                }
                if (bmp != null) Image(bmp.asImageBitmap(), "PDF-Seite ${page + 1}",
                    modifier = Modifier.fillMaxWidth().heightIn(max = 530.dp), contentScale = ContentScale.Fit)
                else Text("PDF kann nicht angezeigt werden", color = Color(0xFFF2B8B5))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(enabled = page > 0, onClick = { page-- }) { Text("Zurück") }
                    Spacer(Modifier.weight(1f))
                    TextButton(enabled = page + 1 < pages, onClick = { page++ }) { Text("Weiter") }
                }
            }
        }
    }
}

private fun thumbnail(file: File, isVideo: Boolean): Bitmap? = try {
    if (isVideo) {
        val retriever = MediaMetadataRetriever()
        try { retriever.setDataSource(file.absolutePath); retriever.getFrameAtTime(0) } finally { retriever.release() }
    } else {
        val bitmap = BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = 3 })
        if (bitmap == null) null else {
            val orientation = ExifInterface(file.absolutePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
            )
            val matrix = Matrix().apply {
                when (orientation) {
                    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
                    ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
                    ExifInterface.ORIENTATION_TRANSPOSE -> { postRotate(90f); postScale(-1f, 1f) }
                    ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
                    ExifInterface.ORIENTATION_TRANSVERSE -> { postRotate(270f); postScale(-1f, 1f) }
                    ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
                }
            }
            if (orientation == ExifInterface.ORIENTATION_NORMAL || orientation == ExifInterface.ORIENTATION_UNDEFINED)
                bitmap
            else Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }
    }
} catch (_: Exception) { null }

private fun openMedia(context: android.content.Context, file: File, mimeType: String, onMessage: (String) -> Unit) {
    try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        context.startActivity(Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        })
    } catch (e: Exception) { onMessage("Keine passende App zum Öffnen vorhanden: ${e.message}") }
}

@Composable
private fun TextEditor(repo: DiaryRepository, elementId: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var field by remember(elementId) { mutableStateOf(TextFieldValue("")) }
    val text = field.text
    var loaded by remember(elementId) { mutableStateOf(false) }
    var isDictation by remember(elementId) { mutableStateOf(false) }
    var listening by remember(elementId) { mutableStateOf(false) }
    var paused by remember(elementId) { mutableStateOf(false) }
    var status by remember(elementId) { mutableStateOf("Bereit") }
    LaunchedEffect(elementId) {
        val element = repo.dao.element(elementId)
        field = TextFieldValue(element?.text.orEmpty())
        isDictation = element?.type == "DICTATION"
        loaded = true
    }
    LaunchedEffect(elementId, text, loaded) {
        if (loaded) { delay(450); repo.saveText(elementId, text) }
    }
    val controller = remember(elementId) {
        OfflineDictation(context, onChunk = { chunk ->
            // Only the corrected/final recognized text is permanently stored.
            val pos = field.selection.start.coerceIn(0, field.text.length)
            val prefix = if (pos > 0 && !field.text[pos - 1].isWhitespace()) " " else ""
            val insertion = prefix + chunk + if (pos < field.text.length && !field.text[pos].isWhitespace()) " " else ""
            val next = field.text.substring(0, pos) + insertion + field.text.substring(pos)
            field = TextFieldValue(next, selection = TextRange(pos + insertion.length))
        }, onState = { status = it })
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) { controller.start(); listening = true; paused = false }
        else status = "Mikrofonberechtigung verweigert"
    }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val activity = context as ComponentActivity
    val currentText by rememberUpdatedState(text)
    DisposableEffect(lifecycle, elementId) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                controller.pause()
                paused = listening
                activity.lifecycleScope.launch { repo.saveText(elementId, currentText) }
            }
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose {
            lifecycle.lifecycle.removeObserver(observer)
            controller.stop()
            activity.lifecycleScope.launch { repo.saveText(elementId, currentText) }
        }
    }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(if (isDictation) "Diktat als Text" else "Text bearbeiten", color = Gold, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text("Änderungen werden während der Eingabe gespeichert.", color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(value = field, onValueChange = { field = it },
            modifier = Modifier.fillMaxWidth().weight(1f), label = { Text("Dein Tagebucheintrag") },
            placeholder = { Text("Was ist passiert?") })
        Spacer(Modifier.height(10.dp))
            Text(status, color = Muted, maxLines = 2, fontSize = 12.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!listening) Button(onClick = { permission.launch(Manifest.permission.RECORD_AUDIO) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Mic, null); Spacer(Modifier.width(4.dp)); Text("Start")
                } else {
                    OutlinedButton(onClick = {
                        if (paused) { controller.resume(); paused = false } else { controller.pause(); paused = true }
                    }, modifier = Modifier.weight(1f)) {
                        Icon(if (paused) Icons.Default.PlayArrow else Icons.Default.Pause, null)
                        Text(if (paused) "Fortsetzen" else "Pause")
                    }
                    Button(onClick = { controller.stop(); listening = false; paused = false }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Stop, null); Text("Stopp")
                    }
                }
            }
        Spacer(Modifier.height(8.dp))
        Button(onClick = { controller.stop(); scope.launch { repo.saveText(elementId, text); onBack() } },
            modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Check, null); Spacer(Modifier.width(6.dp)); Text("Übernehmen") }
    }
}

@Composable
private fun EntryTimeDialog(entry: Entry, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    val context = LocalContext.current
    var date by remember(entry.id) { mutableStateOf(entry.entryDate) }
    var time by remember(entry.id) { mutableStateOf(entry.displayTime) }
    var invalid by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Datum und Uhrzeit") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(date, { date = it }, label = { Text("Datum JJJJ-MM-TT") }, singleLine = true)
            OutlinedButton(onClick = {
                val d = runCatching { LocalDate.parse(date) }.getOrDefault(LocalDate.now())
                DatePickerDialog(context, { _, y, m, day -> date = LocalDate.of(y, m + 1, day).toString() },
                    d.year, d.monthValue - 1, d.dayOfMonth).show()
            }) { Text("Kalender öffnen") }
            OutlinedTextField(time, { time = it }, label = { Text("Uhrzeit HH:mm") }, singleLine = true)
            OutlinedButton(onClick = {
                val parsed = runCatching { LocalTime.parse(time) }.getOrDefault(LocalTime.now())
                TimePickerDialog(context, { _, h, m -> time = "%02d:%02d".format(h, m) }, parsed.hour, parsed.minute, true).show()
            }) { Text("Uhrzeit auswählen") }
            if (invalid) Text("Ungültiges Datum oder Uhrzeit", color = Color(0xFFF2B8B5))
        } }, confirmButton = { TextButton(onClick = {
            if (runCatching { LocalDate.parse(date); LocalTime.parse(time) }.isSuccess) onSave(date, time)
            else invalid = true
        }) { Text("Speichern") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } })
}

@Composable
private fun SettingsPage(repo: DiaryRepository, security: PatternSecurity, onBackup: () -> Unit, onRestore: () -> Unit,
    onBriefstudioImport: () -> Unit, briefstudioConnected: Boolean) {
    var changePattern by remember { mutableStateOf(false) }
    var changePin by remember { mutableStateOf(false) }
    var newPin by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    val mediaSize = remember { repo.mediaFolder.listFiles()?.sumOf { it.length() } ?: 0L }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Datensicherung", color = Gold, fontWeight = FontWeight.SemiBold)
        Text("Dein Tagebuch bleibt lokal. Nur freigegebene Texte aus Briefstudio werden heruntergeladen.", color = Muted)
        OutlinedButton(onClick = onBriefstudioImport, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.CloudDownload, null)
            Spacer(Modifier.width(8.dp))
            Text(if (briefstudioConnected) "Briefstudio: Jetzt synchronisieren" else "Einmalig mit Briefstudio verbinden")
        }
        Text(if (briefstudioConnected) "Verbunden. Keine erneute Anmeldung nötig. Neue Inhalte werden beim Entsperren übernommen; im Hintergrund wird ungefähr alle 15 Minuten geprüft (Android kann verzögern)." else "Einmal anmelden; dein Passwort wird nicht gespeichert. Bereits importierte Dokumente werden nicht verdoppelt.", color = Muted, fontSize = 12.sp)
        Button(onClick = onBackup, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Save, null); Spacer(Modifier.width(8.dp)); Text("Vollständige Sicherung erstellen")
        }
        OutlinedButton(onClick = onRestore, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Restore, null); Spacer(Modifier.width(8.dp)); Text("Sicherung einlesen")
        }
        Text("Wiederherstellung fügt Einträge hinzu und überschreibt keine bestehenden Daten. Aufnahmen können große Sicherungsdateien erzeugen.",
            color = Muted, fontSize = 12.sp)
        HorizontalDivider()
        Text("Zugang", color = Gold, fontWeight = FontWeight.SemiBold)
        OutlinedButton(onClick = { changePattern = true }, modifier = Modifier.fillMaxWidth()) { Text("3×3-Muster ändern") }
        OutlinedButton(onClick = { changePin = true }, modifier = Modifier.fillMaxWidth()) { Text("Vierstelligen Notfall-PIN ändern") }
        HorizontalDivider()
        Text("Speicher", color = Gold, fontWeight = FontWeight.SemiBold)
        Text("Medien und Dateianhänge: ${"%.1f".format(mediaSize / (1024.0 * 1024.0))} MB", color = Muted)
        Text("Gespeichert ausschließlich auf diesem Gerät.", color = Muted)
        HorizontalDivider()
        Text("Memora Solo 0.1.0 – Entwicklungsstand", color = Muted, fontSize = 12.sp)
        Text("Offline-Diktat verwendet die im Gerät verfügbare On-Device-Erkennung. Qualität und Langzeitstabilität sind vor einer Freigabe auf dem S26+ zu testen.",
            color = Muted, fontSize = 12.sp)
    }
    if (changePattern) AlertDialog(onDismissRequest = { changePattern = false }, title = { Text("Neues Muster zeichnen") },
        text = { Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Mindestens vier verschiedene Punkte. Nach dem Speichern wird das bisherige Muster ungültig.")
            PatternPad { result ->
                if (result.length >= 4) {
                    security.changePattern(result)
                    changePattern = false
                    message = "Muster geändert"
                } else message = "Mindestens vier Punkte erforderlich"
            }
        } }, confirmButton = { TextButton(onClick = { changePattern = false }) { Text("Abbrechen") } })
    if (changePin) AlertDialog(onDismissRequest = { changePin = false }, title = { Text("Notfall-PIN ändern") },
        text = { OutlinedTextField(newPin, { newPin = it.filter(Char::isDigit).take(4) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), label = { Text("Neuer PIN") }) },
        confirmButton = { TextButton(enabled = newPin.length == 4, onClick = {
            security.changePin(newPin); newPin = ""; changePin = false; message = "PIN geändert"
        }) { Text("Speichern") } }, dismissButton = { TextButton(onClick = { changePin = false }) { Text("Abbrechen") } })
    if (message.isNotBlank()) AlertDialog(onDismissRequest = { message = "" }, title = { Text("Hinweis") },
        text = { Text(message) }, confirmButton = { TextButton(onClick = { message = "" }) { Text("OK") } })
}

@Composable
private fun BriefstudioLoginDialog(onDismiss: () -> Unit, onImport: (String, String) -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Briefstudio verbinden") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Einmal anmelden. Danach werden freigegebene Texte beim Entsperren automatisch mit dem ausgewählten Datum und der Uhrzeit übernommen.")
                OutlinedTextField(email, { email = it }, label = { Text("Briefstudio-E-Mail") }, singleLine = true)
                OutlinedTextField(password, { password = it }, label = { Text("Briefstudio-Passwort") },
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(), singleLine = true)
                Text("Fotos und das gestaltete Briefpapier werden noch nicht übertragen.", color = Muted, fontSize = 12.sp)
            }
        },
        confirmButton = { TextButton(enabled = email.isNotBlank() && password.isNotBlank(), onClick = { onImport(email, password); password = "" }) { Text("Verbinden") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } })
}
