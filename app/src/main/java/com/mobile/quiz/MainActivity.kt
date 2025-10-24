package com.mobile.quiz

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.lang.Exception
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// -----------------------------------------------------------------------------
// A. TEMA WARNA BARU (DARK BLUE)
// -----------------------------------------------------------------------------

private val DarkBlueColorScheme = darkColorScheme(
    primary = Color(0xFF58A6FF),          // Biru cerah untuk aksen utama (tombol, link)
    onPrimary = Color(0xFF0D1117),        // Teks di atas warna primer (gelap)
    background = Color(0xFF010409),       // Latar belakang utama (sangat gelap)
    surface = Color(0xFF0D1117),          // Warna dasar untuk komponen seperti Card
    onSurface = Color(0xFFC9D1D9),        // Warna teks utama di atas Surface
    secondary = Color(0xFF8B949E),        // Warna sekunder untuk ikon/teks kurang penting
    error = Color(0xFFF85149),            // Warna untuk error
    outline = Color(0xFF30363D)           // Warna untuk garis/outline
)


// -----------------------------------------------------------------------------
// 0. DATA MODEL & UTILS (Tidak ada perubahan)
// -----------------------------------------------------------------------------
data class ActivityItem(
    @get:Exclude
    var documentId: String = "",
    val name: String = "",
    val date: Long = System.currentTimeMillis(),
    val completed: Boolean = false
)

private val dateFormat = SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault())

fun convertMillisToDisplayDate(millis: Long): String {
    return try {
        dateFormat.format(Date(millis))
    } catch (e: Exception) {
        ""
    }
}

// -----------------------------------------------------------------------------
// 1. REPOSITORY (Tidak ada perubahan)
// -----------------------------------------------------------------------------
private const val COLLECTION_NAME = "activities"
private const val TAG_REPO = "FirestoreRepository"
class FirestoreRepository(private val db: FirebaseFirestore) {
    fun getActivities(): Flow<List<ActivityItem>> = callbackFlow {
        val collectionRef = db.collection(COLLECTION_NAME).orderBy("date", Query.Direction.DESCENDING)
        val subscription = collectionRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(TAG_REPO, "Gagal listen ke Firestore.", error)
                trySend(emptyList()).isSuccess
                return@addSnapshotListener
            }
            if (snapshot != null) {
                val activities = snapshot.documents.mapNotNull { doc ->
                    val item = doc.toObject(ActivityItem::class.java)
                    item?.documentId = doc.id
                    item
                }
                trySend(activities).isSuccess
            }
        }
        awaitClose { subscription.remove() }
    }
    suspend fun addActivity(item: ActivityItem) { try { db.collection(COLLECTION_NAME).add(item).await() } catch (e: Exception) { throw e } }
    suspend fun toggleActivityStatus(documentId: String, newStatus: Boolean) { try { db.collection(COLLECTION_NAME).document(documentId).update("completed", newStatus).await() } catch (e: Exception) { throw e } }
    suspend fun deleteActivity(documentId: String) { try { db.collection(COLLECTION_NAME).document(documentId).delete().await() } catch (e: Exception) { throw e } }
    suspend fun editActivity(documentId: String, newName: String) { if (newName.isBlank()) return; try { db.collection(COLLECTION_NAME).document(documentId).update("name", newName).await() } catch (e: Exception) { throw e } }
}

// -----------------------------------------------------------------------------
// 2. VIEWMODEL (Tidak ada perubahan)
// -----------------------------------------------------------------------------
class ActivityViewModel(private val repository: FirestoreRepository = FirestoreRepository(FirebaseFirestore.getInstance())) : ViewModel() {
    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()
    private val _activities = MutableStateFlow<List<ActivityItem>>(emptyList())
    val activities: StateFlow<List<ActivityItem>> = _activities.asStateFlow()
    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()
    init { fetchActivities() }
    private fun fetchActivities() { viewModelScope.launch { _loading.value = true; repository.getActivities().collect { _activities.value = it; _loading.value = false } } }
    fun addActivity(name: String) { if (name.isBlank()) { _toastMessage.value = "Nama kegiatan tidak boleh kosong."; return }; viewModelScope.launch { try { repository.addActivity(ActivityItem(name = name)); _toastMessage.value = "Kegiatan berhasil ditambahkan!" } catch (e: Exception) { _toastMessage.value = "Gagal menambahkan: ${e.message}" } } }
    fun toggleActivityStatus(item: ActivityItem) { viewModelScope.launch { try { repository.toggleActivityStatus(item.documentId, !item.completed) } catch (e: Exception) { _toastMessage.value = "Gagal mengubah status: ${e.message}" } } }
    fun deleteActivity(item: ActivityItem) { viewModelScope.launch { try { repository.deleteActivity(item.documentId); _toastMessage.value = "Kegiatan berhasil dihapus." } catch (e: Exception) { _toastMessage.value = "Gagal menghapus: ${e.message}" } } }
    fun editActivity(item: ActivityItem, newName: String) { if (newName.isBlank() || newName == item.name) return; viewModelScope.launch { try { repository.editActivity(item.documentId, newName); _toastMessage.value = "Kegiatan berhasil diubah." } catch (e: Exception) { _toastMessage.value = "Gagal mengedit: ${e.message}" } } }
    fun toastShown() { _toastMessage.value = null }
}

// -----------------------------------------------------------------------------
// 3. MAIN ACTIVITY & UI
// -----------------------------------------------------------------------------
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseApp.initializeApp(this)
        setContent {
            // PERUBAHAN UTAMA: Menerapkan Color Scheme kustom ke MaterialTheme
            MaterialTheme(colorScheme = DarkBlueColorScheme) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    ActivityListScreen()
                }
            }
        }
    }
}

// --- Composable di bawah ini tidak diubah strukturnya, hanya akan beradaptasi dengan tema baru ---

@Composable
fun ActivityListScreen(viewModel: ActivityViewModel = viewModel()) {
    val activities by viewModel.activities.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }
    var showEditDialogFor by remember { mutableStateOf<ActivityItem?>(null) }
    val context = LocalContext.current
    val toastMessage by viewModel.toastMessage.collectAsStateWithLifecycle()

    LaunchedEffect(toastMessage) {
        toastMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.toastShown()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background, // Menggunakan warna latar dari tema
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) { // Tombol ini akan otomatis menggunakan warna 'primary' dari tema
                Icon(Icons.Filled.Add, contentDescription = "Tambah Kegiatan")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            Text(
                text = "Daftar Kegiatan",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp),
                color = MaterialTheme.colorScheme.primary // Judul akan menggunakan warna 'primary' dari tema
            )
            HorizontalDivider(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                color = MaterialTheme.colorScheme.outline // Garis menggunakan warna 'outline' dari tema
            )

            when {
                loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator() // Indikator loading akan otomatis menggunakan warna 'primary'
                    }
                }
                activities.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Belum ada kegiatan. Tekan '+' untuk menambah.", color = MaterialTheme.colorScheme.secondary)
                    }
                }
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(activities, key = { it.documentId }) { activity ->
                            ActivityCard(
                                activity = activity,
                                onToggleCompletion = { viewModel.toggleActivityStatus(activity) },
                                onEdit = { showEditDialogFor = activity },
                                onDelete = { viewModel.deleteActivity(activity) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddOrEditActivityDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name ->
                viewModel.addActivity(name)
                showAddDialog = false
            }
        )
    }

    showEditDialogFor?.let { activityToEdit ->
        AddOrEditActivityDialog(
            initialValue = activityToEdit.name,
            onDismiss = { showEditDialogFor = null },
            onConfirm = { newName ->
                viewModel.editActivity(activityToEdit, newName)
                showEditDialogFor = null
            }
        )
    }
}

@Composable
fun ActivityCard(
    activity: ActivityItem,
    onToggleCompletion: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    // Logika warna teks tetap sama, namun sumber warnanya berasal dari tema baru
    val textColor = if (activity.completed) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface
    val textDecoration = if (activity.completed) TextDecoration.LineThrough else null

    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = activity.completed,
                onCheckedChange = { onToggleCompletion() }
            )
            Column(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                Text(
                    text = activity.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = textColor,
                    textDecoration = textDecoration
                )
                Text(
                    text = "Dibuat: ${convertMillisToDisplayDate(activity.date)}",
                    fontSize = 12.sp,
                    color = textColor.copy(alpha = 0.7f)
                )
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Filled.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.secondary)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Hapus", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
fun AddOrEditActivityDialog(
    initialValue: String = "",
    onDismiss: () -> Unit,
    onConfirm: (name: String) -> Unit
) {
    var text by remember { mutableStateOf(initialValue) }
    val isEditing = initialValue.isNotEmpty()
    val title = if (isEditing) "Edit Kegiatan" else "Tambah Kegiatan Baru"
    val confirmButtonText = if (isEditing) "Simpan" else "Tambah"
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Nama Kegiatan") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(text) },
                enabled = text.isNotBlank()
            ) {
                Text(confirmButtonText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
fun UncompletedActivityPreview() {
    MaterialTheme(colorScheme = DarkBlueColorScheme) {
        Surface {
            ActivityCard(
                activity = ActivityItem(documentId = "1", name = "Belajar Jetpack Compose", completed = false),
                onToggleCompletion = {}, onEdit = {}, onDelete = {}
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun CompletedActivityPreview() {
    MaterialTheme(colorScheme = DarkBlueColorScheme) {
        Surface {
            ActivityCard(
                activity = ActivityItem(documentId = "2", name = "Mengerjakan Tugas Mobile", completed = true),
                onToggleCompletion = {}, onEdit = {}, onDelete = {}
            )
        }
    }
}