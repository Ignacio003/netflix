package com.example.netflix

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.ui.PlayerView
import androidx.navigation.compose.composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import coil.annotation.ExperimentalCoilApi
import coil.compose.rememberImagePainter
import com.example.netflix.ui.theme.NetflixTheme
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import org.json.JSONObject
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.POST
import retrofit2.Response
import retrofit2.Retrofit

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
                        .background(color = colorResource(id = R.color.blue_background))
                ) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .padding(innerPadding)
                            .fillMaxSize()
                            .background(color = colorResource(id = R.color.blue_background)),
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

    @GET("media/{fileName}")
    suspend fun streamMedia(
        @Header("Authorization") token: String,
        @Path("fileName") fileName: String
    ): Response<ResponseBody>
}

val retrofit = Retrofit.Builder().baseUrl("http://34.175.133.0:8080/").addConverterFactory(GsonConverterFactory.create()).build()

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
            Log.d("ChunkServer", "Chunked file ${file.name} into ${chunks.size} chunks")
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

suspend fun discoverPeers(port: Int): List<InetAddress> = withContext(Dispatchers.IO) {
    val peers = mutableListOf<InetAddress>()
    val socket = DatagramSocket()
    socket.broadcast = true
    val message = "DISCOVER_PEERS".toByteArray()
    val broadcastAddress = InetAddress.getByName("255.255.255.255")
    val packet = DatagramPacket(message, message.size, broadcastAddress, port)
    Log.d("discoverPeers", "Sending broadcast packet to: ${broadcastAddress.hostAddress}")
    socket.send(packet)
    val buffer = ByteArray(1024)
    val responsePacket = DatagramPacket(buffer, buffer.size)
    socket.soTimeout = 1500
    try {
        while (true) {
            socket.receive(responsePacket)
            Log.d("discoverPeers", "Received response from: ${responsePacket.address.hostAddress}")
            peers.add(responsePacket.address)
        }
    } catch (e: Exception) {

    }
    socket.close()
    peers.forEach { peer ->
        Log.d("discoverPeers", "Discovered peer: ${peer.hostAddress}")
    }
    peers
}

fun startChunkServer(port: Int, fileChunks: Map<String, List<Chunk>>) {
    try {
        val serverSocket = ServerSocket(port)
        val networkInterfaces = NetworkInterface.getNetworkInterfaces()
        var serverAddress: String? = null
        while (networkInterfaces.hasMoreElements()) {
            val networkInterface = networkInterfaces.nextElement()
            val inetAddresses = networkInterface.inetAddresses
            while (inetAddresses.hasMoreElements()) {
                val inetAddress = inetAddresses.nextElement()
                if (!inetAddress.isLoopbackAddress && inetAddress is Inet4Address) {
                    serverAddress = inetAddress.hostAddress
                    break
                }
            }
            if (serverAddress != null) break
        }
        Log.d("ChunkServer", "Chunk server started on $serverAddress:$port")
        Thread {
            val broadcastSocket = DatagramSocket(port, InetAddress.getByName("0.0.0.0"))
            broadcastSocket.broadcast = true
            val buffer = ByteArray(1024)
            while (true) {
                val packet = DatagramPacket(buffer, buffer.size)
                try {
                    broadcastSocket.receive(packet)
                    val message = String(packet.data, 0, packet.length)
                    if (message == "DISCOVER_PEERS") {
                        val responseMessage = "PEER_RESPONSE".toByteArray()
                        val responsePacket = DatagramPacket(
                            responseMessage, responseMessage.size, packet.address, packet.port
                        )
                        broadcastSocket.send(responsePacket)
                        Log.d("ChunkServer", "Responded to broadcast from ${packet.address.hostAddress}")
                    }
                } catch (e: IOException) {
                    Log.e("ChunkServer", "Error receiving broadcast: ${e.message}")
                }
            }
        }.start()

        while (true) {
            try {
                val clientSocket = serverSocket.accept()
                Log.d("ChunkServer", "Accepted connection from ${clientSocket.inetAddress.hostAddress}")
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
        Log.d("ChunkServer", "Received request for file: $fileName, chunk: $chunkIndex from ${clientSocket.inetAddress.hostAddress}")

        val chunks = fileChunks[fileName]
        val chunk = chunks?.find { it.index == chunkIndex }
        Log.d("ChunkServer", "Finding chunk $chunkIndex for file $fileName")
        if (chunk != null) {
            Log.d("ChunkServer", "Found chunk $chunkIndex for file $fileName")
            output.writeInt(chunk.data.size)
            output.write(chunk.data)
        } else {
            Log.d("ChunkServer", "Chunk $chunkIndex for file $fileName not found")
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
    return try {
        val socket = Socket(peer, port)
        val input = DataInputStream(socket.getInputStream())
        val output = DataOutputStream(socket.getOutputStream())
        output.writeUTF(fileName)
        output.writeInt(chunkIndex)
        val chunkSize = input.readInt()
        val chunkData = if (chunkSize > 0) {
            val data = ByteArray(chunkSize)
            input.readFully(data)
            data
        } else {
            Log.d("ChunkRequest", "Peer ${peer.hostAddress} does not have the movie or chunk $chunkIndex for file $fileName")
            null
        }
        socket.close()
        chunkData
    } catch (e: Exception) {
        Log.e("ChunkRequest", "Error requesting chunk $chunkIndex from peer ${peer.hostAddress}: ${e.message}")
        null
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
        title = {
            if(name == "Netflix+") {
                Image(
                    painter = painterResource(id = R.drawable.logo_app),
                    contentDescription = "App logo",
                    modifier = Modifier
                        .height(50.dp)
                        .aspectRatio(1f)
                )
            }else{
                Text(text = name)
            }
        },
        modifier = modifier,
        navigationIcon = {
            if (showBackButton) {
                IconButton(onClick = { onBackClick?.invoke() }) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Go back",
                        tint = Color.White
                    )
                }
            }
        },
        actions = {
            IconButton(onClick = { onProfileClick?.invoke() }) {
                Icon(
                    Icons.Filled.Person,
                    contentDescription = "Profile",
                    tint = Color.White
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = colorResource(id = R.color.blue_background),
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
            .background(color = colorResource(id = R.color.blue_background)),
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
                                val token = it.token
                                UserSession.token = token
                                UserSession.username = username
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(navController: NavController) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    Box(modifier = Modifier.fillMaxSize()) {
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
                                val response =
                                    mediaApiService.registerUser(User(username, password, false))
                                if (response.isSuccessful) {
                                    val registerResponse = response.body()
                                    if (registerResponse?.message == "User registered successfully!") {
                                        navController.navigate("login")
                                    } else {
                                        errorMessage =
                                            registerResponse?.message ?: "Registration failed"
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
        TopAppBar(
            title = {},
            navigationIcon = {
                IconButton(onClick = { navController.navigate("login") }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = colorResource(id = R.color.blue_background)
            ),
            modifier = Modifier.align(Alignment.TopStart)
        )
    }
}

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
            .clip(RoundedCornerShape(8.dp))
            .background(color = colorResource(id = R.color.blue_button))
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
    val isSearching = remember { mutableStateOf(false) }
    val searchQuery = remember { mutableStateOf("") }

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
            .background(color = colorResource(id = R.color.blue_background)),
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
            isSearching = isSearching.value,
            searchQuery = searchQuery.value,
            onSearchQueryChange = { query -> searchQuery.value = query },
            onSearchToggle = {
                isSearching.value = !isSearching.value
                if (!isSearching.value) searchQuery.value = ""
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
                items(mediaList.filter { it.title.contains(searchQuery.value, ignoreCase = true) }) { media ->
                    MediaBox(media, navController)
                }
            }
        }
    }
}

@Composable
fun SuperiorPart(
    name: String,
    showBackButton: Boolean,
    onBackClick: () -> Unit,
    isSearching: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSearchToggle: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(isSearching) {
        if (isSearching) {
            focusRequester.requestFocus()
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colorResource(id = R.color.blue_background))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showBackButton) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Color.White,
                modifier = Modifier
                    .size(24.dp)
                    .clickable { onBackClick() }
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        if (isSearching) {
            TextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
                    .background(Color.White, shape = RoundedCornerShape(8.dp)),
                placeholder = { Text("Search...", color = Color.Gray) },
                singleLine = true
            )
        } else {
            Text(
                text = name,
                color = Color.White,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Icon(
            imageVector = if (isSearching) Icons.Default.Close else Icons.Default.Search,
            contentDescription = if (isSearching) "Close Search" else "Search",
            tint = Color.White,
            modifier = Modifier
                .size(24.dp)
                .clickable {
                    onSearchToggle()
                    if (!isSearching) onSearchQueryChange("")
                }
        )
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
            .clip(RoundedCornerShape(8.dp))
            .background(color = colorResource(id = R.color.blue_button))
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

@UnstableApi
class TokenDataSourceFactory(private val token: String) : DataSource.Factory {
    override fun createDataSource(): DataSource {
        return DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(mapOf("Authorization" to token))
            .createDataSource()
    }
}

suspend fun downloadFromPeers(peers: List<InetAddress>, fileName: String, localPath: String): Boolean {
    return withContext(Dispatchers.IO) {
        val chunks = mutableListOf<Chunk>()
        var chunkIndex = 0
        while (true) {
            var chunkData: ByteArray? = null
            for (peer in peers) {
                chunkData = requestChunk(peer, 8081, fileName, chunkIndex)
                if (chunkData != null) {
                    chunks.add(Chunk(chunkIndex, chunkData, ""))
                    break
                }
            }
            if (chunkData == null) {
                if (chunkIndex == 0) {
                    // archivo no existe en los peers
                    return@withContext false
                } else {
                    val file = File(localPath)
                    reassembleFile(chunks, file)
                    Log.d("TorrentDownload", "File assembled from ${chunks.size} chunks at $localPath")
                    return@withContext true
                }
            }
            chunkIndex++
        }
        return@withContext false
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

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build()
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    LaunchedEffect(videoUrl) {
        if (videoUrl.isNullOrBlank()) {
            videoLoading.value = false
            return@LaunchedEffect
        }

        if (fileExists) {
            Log.d("Player", "Playing local file: $localFilePath")
            val uri = Uri.parse(localFilePath)
            exoPlayer.setMediaItem(MediaItem.fromUri(uri))
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
            videoLoading.value = false
        } else {
            val peers = discoverPeers(8081)
            Log.d("Player", "Discovered ${peers.size} peers")
            if (peers.isNotEmpty() && localFileName != null) {

                val success = downloadFromPeers(peers, localFileName, localFilePath)

                if (success) {
                    Log.d("Player", "Playing from peers file: $localFilePath")
                    fileExists = true
                    exoPlayer.setMediaItem(MediaItem.fromUri(Uri.parse(localFilePath)))
                    exoPlayer.prepare()
                    exoPlayer.playWhenReady = true
                    videoLoading.value = false
                    return@LaunchedEffect
                }
            }

            val hlsUrl = if (videoUrl.contains("_360p")) {
                videoUrl.replace("_360p.mp4", "_hls_360p/playlist.m3u8")
            } else {
                videoUrl.replace(".mp4", "_hls_1080p/playlist.m3u8")
            }

            if (UserSession.token != null) {
                Log.d("Player", "Playing HLS from server: $hlsUrl")
                val dataSourceFactory = TokenDataSourceFactory(UserSession.token!!)
                val mediaSource = HlsMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(hlsUrl))
                exoPlayer.setMediaSource(mediaSource)
                exoPlayer.prepare()
                exoPlayer.playWhenReady = true
            }
            videoLoading.value = false
        }

        exoPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    videoLoading.value = false
                }
            }
        })
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            factory = {
                PlayerView(context).apply {
                    player = exoPlayer
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

        if (!fileExists && !videoLoading.value) {
            FloatingActionButton(
                onClick = {
                    coroutineScope.launch {
                        downloadInProgress.value = true
                        val success = downloadVideoWithToken(context, videoUrl!!, localFilePath, UserSession.token!!)
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

suspend fun downloadVideoWithToken(context: Context, videoUrl: String, localPath: String, token: String): Boolean {
    return withContext(Dispatchers.IO) {
        try {
            val response = mediaApiService.streamMedia(token, videoUrl.substringAfterLast("/"))
            if (response.isSuccessful) {
                val inputStream = response.body()?.byteStream()
                val file = File(localPath)
                file.outputStream().use { output ->
                    inputStream?.copyTo(output)
                }
                Log.d("Download", "Downloaded video to $localPath")
                true
            } else {
                Log.e("Download", "Error downloading video: ${response.errorBody()?.string()}")
                false
            }
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