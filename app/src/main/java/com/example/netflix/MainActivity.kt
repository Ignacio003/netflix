package com.example.netflix

import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import coil.annotation.ExperimentalCoilApi
import coil.compose.rememberImagePainter
import com.example.netflix.ui.theme.NetflixTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import java.io.IOException
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.ui.res.colorResource
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.ui.res.painterResource
import androidx.navigation.compose.*
import androidx.compose.foundation.clickable
import kotlinx.coroutines.withContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.json.JSONObject
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import android.content.Context
import android.os.Environment
import android.view.View
import androidx.compose.material.icons.filled.Download
import androidx.media3.common.Player
import java.io.File
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.withContext
import retrofit2.http.Header
import java.io.FileInputStream
import java.security.MessageDigest
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.net.ServerSocket
import java.net.Socket
import java.io.FileOutputStream

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_Netflix)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NetflixTheme {
                val navController = rememberNavController()
                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                ) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .padding(innerPadding)
                            .fillMaxSize()
                            .background(Color.Black),
                        verticalArrangement = Arrangement.Top
                    ) {
                        NetflixNavHost(navController)
                    }
                }
            }
        }
        val moviesDir = getExternalFilesDir(Environment.DIRECTORY_MOVIES)
        if (moviesDir != null) {
            val fileChunks = chunkAllFilesInDirectory(moviesDir, 1024 * 1024)
            Thread { startChunkServer(8081, fileChunks) }.start()

        }
    }
}
object UserSession {
    var token: String? = null
    var username: String? = null
}

data class LoginResponse(
    val message: String,
    val token: String
)

interface MediaApiService {
    @GET("media/category/{category}")
    suspend fun getMediaByCategory(
        @Header("Authorization") token: String,
        @Path("category") category: String
    ): List<Media>

    @POST("users/login")
    suspend fun loginUser(@Body user: User): Response<LoginResponse>

    @POST("users/register")
    suspend fun registerUser(@Body user: User): Response<LoginResponse>

}

val retrofit = Retrofit.Builder()
    .baseUrl("http://34.175.133.0:8080/")
    .addConverterFactory(GsonConverterFactory.create())
    .build()

val mediaApiService: MediaApiService = retrofit.create(MediaApiService::class.java)

data class User(
    val username: String,
    val passwordHash: String,
    val esAdmin: Boolean = false
)

data class Media(
    val mediaId: Int,
    val title: String,
    val description: String,
    val highResUrl: String,
    val lowResUrl: String,
    val category: String,
    val hlsUrl1080p: String,
    val hlsUrl360p: String
)


data class Chunk(val index: Int, val data: ByteArray, val hash: String)

fun chunkAllFilesInDirectory(directory: File, chunkSize: Int): Map<String, List<Chunk>> {
    val fileChunks = mutableMapOf<String, List<Chunk>>()

    directory.listFiles()?.forEach { file ->
        if (file.isFile) {
            val chunks = chunkFile(file, chunkSize)
            fileChunks[file.name] = chunks

        }
    }
    return fileChunks
}
fun chunkFile(file: File, chunkSize: Int): List<Chunk> {
    val chunks = mutableListOf<Chunk>()
    val buffer = ByteArray(chunkSize)
    val digest = MessageDigest.getInstance("SHA-256")
    FileInputStream(file).use { fis ->
        var bytesRead: Int
        var index = 0
        while (fis.read(buffer).also { bytesRead = it } != -1) {
            val chunkData = buffer.copyOf(bytesRead)
            val hash = digest.digest(chunkData).joinToString("") { "%02x".format(it) }
            chunks.add(Chunk(index++, chunkData, hash))
        }
    }
    return chunks
}
fun chunkFiles(files: List<File>, chunkSize: Int): Map<String, List<Chunk>> {
    val fileChunks = mutableMapOf<String, List<Chunk>>()
    files.forEach { file ->
        val chunks = chunkFile(file, chunkSize)
        fileChunks[file.name] = chunks
    }
    return fileChunks
}
suspend fun discoverPeers(port: Int): List<InetAddress> = withContext(Dispatchers.IO) {
    val peers = mutableListOf<InetAddress>()
    val socket = DatagramSocket()
    socket.broadcast = true
    val message = "DISCOVER_PEERS".toByteArray()
    val packet = DatagramPacket(message, message.size, InetAddress.getByName("255.255.255.255"), port)
    Log.d("discoverPeers", "Sending broadcast packet to ${packet.address}:${packet.port}")
    socket.send(packet)

    val buffer = ByteArray(1024)
    val responsePacket = DatagramPacket(buffer, buffer.size)
    socket.soTimeout = 100000 // 10 seconds timeout
    try {
        while (true) {
            socket.receive(responsePacket)
            Log.d("discoverPeers", "Received response from: ${responsePacket.address}")
            peers.add(responsePacket.address)
        }
    } catch (e: Exception) {
        Log.d("discoverPeers", "Timeout reached or error: ${e.message}")
    }
    socket.close()
    Log.d("discoverPeers", "Peers discovered: ${peers.size}")
    peers
}
fun startChunkServer(port: Int, fileChunks: Map<String, List<Chunk>>) {
    try {
        val serverSocket = ServerSocket(port)
        Log.d("ChunkServer", "Chunk server started on port $port")
        while (true) {
            try {
                val clientSocket = serverSocket.accept()
                Log.d("ChunkServer", "Accepted connection from ${clientSocket.inetAddress}")
                Thread { handleClient(clientSocket, fileChunks) }.start()
            } catch (e: IOException) {
                Log.e("ChunkServer", "Error accepting connection: ${e.message}")
            }
        }
    } catch (e: IOException) {
        Log.e("ChunkServer", "Error starting server: ${e.message}")
    }
}
fun handleClient(clientSocket: Socket, fileChunks: Map<String, List<Chunk>>) {
    try {
        val input = DataInputStream(clientSocket.getInputStream())
        val output = DataOutputStream(clientSocket.getOutputStream())
        val fileName = input.readUTF()
        val chunkIndex = input.readInt()
        val chunks = fileChunks[fileName]
        val chunk = chunks?.find { it.index == chunkIndex }
        if (chunk != null) {
            output.writeInt(chunk.data.size)
            output.write(chunk.data)
        } else {
            output.writeInt(0)
        }
    } catch (e: EOFException) {
        Log.e("ChunkServer", "Client disconnected unexpectedly: ${e.message}")
    } catch (e: IOException) {
        Log.e("ChunkServer", "I/O error: ${e.message}")
    } finally {
        clientSocket.close()
    }
}
fun requestChunk(peer: InetAddress, port: Int, fileName: String, chunkIndex: Int): ByteArray? {
    val socket = Socket(peer, port)
    val input = DataInputStream(socket.getInputStream())
    val output = DataOutputStream(socket.getOutputStream())
    output.writeUTF(fileName)
    output.writeInt(chunkIndex)
    val chunkSize = input.readInt()
    return if (chunkSize > 0) {
        val chunkData = ByteArray(chunkSize)
        input.readFully(chunkData)
        chunkData
    } else {
        null
    }.also {
        socket.close()
    }
}
fun reassembleFile(chunks: List<Chunk>, outputFile: File) {
    FileOutputStream(outputFile).use { fos ->
        chunks.sortedBy { it.index }.forEach { chunk ->
            fos.write(chunk.data)
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuperiorPart(
    name: String,
    modifier: Modifier = Modifier,
    showBackButton: Boolean = false,
    onBackClick: (() -> Unit)? = null,
    onProfileClick: (() -> Unit)? = null
) {
    TopAppBar(
        title = { Text(text = name) },
        modifier = modifier,
        navigationIcon = {
            if (showBackButton) {
                IconButton(onClick = { onBackClick?.invoke() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Go back", tint = Color.White)
                }
            }
        },
        actions = {
            IconButton(onClick = { onProfileClick?.invoke() }) {
                Icon(Icons.Filled.Person, contentDescription = "Profile", tint = Color.White)
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color(0xFF1B0033),
            titleContentColor = Color.White
        )
    )
}

@Composable
fun NetflixNavHost(navController: NavHostController) {
    NavHost(navController, startDestination = "login") {
        composable("login") {
            LoginScreen(navController = navController)
        }
        composable("register") {
            RegisterScreen(navController = navController)
        }
        composable("categories") {
            Column {
                SuperiorPart(
                    name = "Netflix+",
                    onProfileClick = { navController.navigate("profile") }
                )
                CategoryGrid(navController = navController)
            }
        }
        composable("category/{categoryName}") { backStackEntry ->
            val categoryName = backStackEntry.arguments?.getString("categoryName")
            CategoryScreen(navController = navController, categoryName = categoryName)
        }
        composable("player/{videoUrl}") { backStackEntry ->
            val videoUrl = backStackEntry.arguments?.getString("videoUrl")
            PlayerScreen(navController = navController, videoUrl = videoUrl)
        }
        composable("profile") {
            ProfileScreen(navController = navController)
        }
    }
}

@Composable
fun CategoryGrid(navController: NavController) {
    val categories = listOf("Series", "Films", "Documentaries", "Kids")
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier
            .padding(20.dp)
            .background(Color.Black),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(categories) { category ->
            CategoryBox(categoryName = category, onClick = {
                navController.navigate("category/$category")
            })
        }
    }
}
@Composable
fun LoginScreen(navController: NavController, modifier: Modifier = Modifier) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(color = colorResource(id = R.color.blue_background))
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(id = R.drawable.logo_app),
            contentDescription = "App logo",
            modifier = Modifier
                .size(200.dp)
                .padding(bottom = 24.dp)
        )
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Username") },
            textStyle = LocalTextStyle.current.copy(color = Color.White),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            textStyle = LocalTextStyle.current.copy(color = Color.White),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                        contentDescription = if (passwordVisible) "Hide password" else "Show password"
                    )
                }
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                coroutineScope.launch {
                    try {
                        val response = mediaApiService.loginUser(User(username, password, false))
                        if (response.isSuccessful) {
                            val loginResponse = response.body()
                            loginResponse?.let {
                                // Extraer el token directamente desde loginResponse
                                val token = it.token

                                // Guardar el token en UserSession o en SharedPreferences
                                UserSession.token = token
                                UserSession.username = username

                                // Log del token
                                Log.d("LoginToken", "Token obtenido: $token")

                                // Navegar a la pantalla siguiente
                                navController.navigate("categories")
                            } ?: run {
                                errorMessage = "Unexpected response format"
                            }
                        } else {
                            val errorJson = response.errorBody()?.string()
                            val errorObj = JSONObject(errorJson)
                            errorMessage = errorObj.getString("message")
                        }
                    } catch (e: Exception) {
                        errorMessage = "Error: ${e.message}"
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = colorResource(id = R.color.blue_button),
                contentColor = Color.White
            )
        ) {
            Text("Login")
        }

        errorMessage?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = it, color = Color.Red)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Don't have an account? Register",
            color = Color.White,
            modifier = Modifier
                .padding(top = 8.dp)
                .clickable { navController.navigate("register") }
        )
    }
}
@Composable
fun RegisterScreen(navController: NavController) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(color = colorResource(id = R.color.blue_background))
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(id = R.drawable.logo_app),
            contentDescription = "App logo",
            modifier = Modifier
                .size(200.dp)
                .padding(bottom = 24.dp)
        )

        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Username") },
            textStyle = LocalTextStyle.current.copy(color = Color.White),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            textStyle = LocalTextStyle.current.copy(color = Color.White),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                        contentDescription = if (passwordVisible) "Hide password" else "Show password"
                    )
                }
            }
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = confirmPassword,
            onValueChange = { confirmPassword = it },
            label = { Text("Confirm Password") },
            textStyle = LocalTextStyle.current.copy(color = Color.White),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                        contentDescription = if (passwordVisible) "Hide password" else "Show password"
                    )
                }
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (password == confirmPassword) {
                    coroutineScope.launch {
                        try {
                            val response = mediaApiService.registerUser(User(username, password, false))
                            if (response.isSuccessful) {
                                val registerResponse = response.body()
                                if (registerResponse?.message == "User registered successfully!") {
                                    navController.navigate("login")
                                } else {
                                    errorMessage = registerResponse?.message ?: "Registration failed"
                                }
                            } else {
                                val errorJson = response.errorBody()?.string()
                                val errorObj = JSONObject(errorJson)
                                errorMessage = errorObj.getString("message")
                            }
                        } catch (e: Exception) {
                            errorMessage = "Error: ${e.message}"
                        }
                    }
                } else {
                    errorMessage = "Passwords do not match"
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = colorResource(id = R.color.blue_button),
                contentColor = Color.White
            )
        ) {
            Text("Register")
        }

        errorMessage?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = it, color = Color.Red)
        }
    }
}
@OptIn(ExperimentalCoilApi::class)
@Composable
fun CategoryBox(categoryName: String, onClick: () -> Unit) {
    val imageRes = when (categoryName) {
        "Series" -> R.drawable.series
        "Films" -> R.drawable.movies
        "Documentaries" -> R.drawable.documentaries
        "Kids" -> R.drawable.kids
        else -> R.drawable.documentaries
    }

    Column(
        modifier = Modifier
            .background(Color(0xFF1B0033))
            .size(180.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Image(
            painter = painterResource(id = imageRes),
            contentDescription = categoryName,
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
            contentScale = ContentScale.Crop
        )

        Spacer(modifier = Modifier.height(15.dp))
        Text(text = categoryName, color = Color.White)
    }
}
@Composable
fun CategoryScreen(navController: NavController, categoryName: String?) {
    val mediaList = remember { mutableStateListOf<Media>() }
    val error = remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(categoryName) {
        if (categoryName != null) {
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val response =
                        UserSession.token?.let { mediaApiService.getMediaByCategory(it, categoryName) }
                    mediaList.clear()
                    if (response != null) {
                        mediaList.addAll(response)
                    }
                } catch (e: IOException) {
                    error.value = "No internet connection: ${e.message}"
                } catch (e: Exception) {
                    error.value = "Error loading media: ${e.message}"
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        SuperiorPart(
            name = categoryName ?: "Category",
            showBackButton = true,
            onBackClick = {
                if (navController.previousBackStackEntry != null) {
                    navController.popBackStack()
                }
            },
            onProfileClick = {
                navController.navigate("profile")
            }
        )

        if (error.value != null) {
            Text(text = error.value ?: "Unknown error", color = Color.Red, textAlign = TextAlign.Center)
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(mediaList) { media ->
                    MediaBox(media, navController)
                }
            }
        }
    }
}

@OptIn(ExperimentalCoilApi::class)
@Composable
fun MediaBox(media: Media, navController: NavController) {
    var showDialog by remember { mutableStateOf(false) }
    var selectedUrl by remember { mutableStateOf<String?>(null) }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(text = "Select Quality") },
            text = { Text("Choose the video quality:") },
            confirmButton = {
                TextButton(onClick = {
                    selectedUrl = media.highResUrl
                    showDialog = false
                    navController.navigate("player/${Uri.encode(selectedUrl)}")
                }) {
                    Text("High Quality")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    selectedUrl = media.lowResUrl
                    showDialog = false
                    navController.navigate("player/${Uri.encode(selectedUrl)}")
                }) {
                    Text("Low Quality")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .background(Color(0xFF1B0033))
            .size(180.dp)
            .clickable {
                showDialog = true
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Image(
            painter = rememberImagePainter(data = media.highResUrl),
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = media.title, color = Color.White, textAlign = TextAlign.Center)
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun PlayerScreen(navController: NavController, videoUrl: String?) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val externalFilesDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)?.absolutePath
    val localFileName = videoUrl?.substringAfterLast("/")?.replace(".m3u8", ".mp4")
    val localFilePath = "$externalFilesDir/$localFileName"
    var fileExists by remember { mutableStateOf(File(localFilePath).exists()) }
    val downloadInProgress = remember { mutableStateOf(false) }
    val videoLoading = remember { mutableStateOf(true) }
    val controlsVisible = remember { mutableStateOf(false) }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            if (fileExists) {
                val uri = Uri.parse(localFilePath)
                setMediaItem(MediaItem.fromUri(uri))
                playWhenReady = true
                Log.d("PlayerScreen", "Playing local file: $uri")
            } else {
                coroutineScope.launch {
                    Log.d("PlayerScreen", "Searching for peers...")
                    val peers = discoverPeers(8081)
                    Log.d("PlayerScreen", "Peers discovered: ${peers.size}")
                    var peerStreamUri: Uri? = null
                    for (peer in peers) {
                        Log.d("PlayerScreen", "Requesting chunk from peer: ${peer.hostAddress}")
                        val chunkData = requestChunk(peer, 8081, localFileName!!, 0)
                        if (chunkData != null) {
                            peerStreamUri = Uri.parse("http://${peer.hostAddress}:8081/$localFileName")
                            break
                        }
                    }
                    if (peerStreamUri != null) {
                        setMediaItem(MediaItem.fromUri(peerStreamUri))
                        Log.d("PlayerScreen", "Streaming from peer: $peerStreamUri")
                    } else if (!videoUrl.isNullOrBlank()) {
                        val hlsUrl = if (videoUrl.contains("_360p")) {
                            videoUrl.replace("_360p.mp4", "_hls_360p/playlist.m3u8")
                        } else {
                            videoUrl.replace(".mp4", "_hls_1080p/playlist.m3u8")
                        }
                        setMediaItem(MediaItem.fromUri(hlsUrl))
                        Log.d("PlayerScreen", "Playing HLS URL: $hlsUrl")
                    }
                    prepare()
                    playWhenReady = true
                }
            }
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_READY) {
                        videoLoading.value = false
                    }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    controlsVisible.value = !isPlaying
                }
            })
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = {
                PlayerView(context).apply {
                    player = exoPlayer
                    setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { visibility ->
                        controlsVisible.value = visibility == View.VISIBLE
                    })
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        if (videoLoading.value) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Color.White
            )
        }

        IconButton(
            onClick = { navController.popBackStack() },
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Go back", tint = Color.White)
        }

        if (!fileExists) {
            FloatingActionButton(
                onClick = {
                    coroutineScope.launch {
                        downloadInProgress.value = true
                        val success = downloadVideo(context, videoUrl!!, localFilePath)
                        if (success) {
                            fileExists = true
                            exoPlayer.setMediaItem(MediaItem.fromUri(Uri.parse(localFilePath)))
                            exoPlayer.prepare()
                            exoPlayer.playWhenReady = true
                        }
                        downloadInProgress.value = false
                    }
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp),
                containerColor = Color.Transparent
            ) {
                if (downloadInProgress.value) {
                    CircularProgressIndicator(color = Color.White)
                } else {
                    Icon(Icons.Filled.Download, contentDescription = "Download", tint = Color.White)
                }
            }
        }
    }
}

suspend fun downloadVideo(context: Context, videoUrl: String, localPath: String): Boolean {
    return withContext(Dispatchers.IO) {
        try {
            val url = URL(videoUrl.replace(".m3u8", ".mp4"))
            val connection = url.openConnection() as HttpURLConnection
            connection.connect()

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("Server returned HTTP ${connection.responseCode} ${connection.responseMessage}")
            }

            val inputStream = connection.inputStream
            val file = File(localPath)
            file.outputStream().use { output ->
                inputStream.copyTo(output)
            }
            Log.d("Download", "Downloaded video to $localPath")
            true
        } catch (e: Exception) {
            Log.e("Download", "Error downloading video: ${e.message}")
            false
        }
    }
}

@Composable
fun ProfileScreen(navController: NavController) {
    val username = UserSession.username ?: "Unknown User"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(color = colorResource(id = R.color.blue_background))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        SuperiorPart(
            name = "Profile",
            showBackButton = true,
            onBackClick = { navController.popBackStack() }
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "Username: $username", color = Color.White)
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                UserSession.username = null
                navController.navigate("login") {
                    popUpTo(navController.graph.startDestinationId) {
                        inclusive = true
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = colorResource(id = R.color.blue_button),
                contentColor = Color.White
            )
        ) {
            Text("Logout")
        }
    }
}