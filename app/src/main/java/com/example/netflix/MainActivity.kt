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


// MainActivity
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Set the main theme
        setTheme(R.style.Theme_Netflix)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NetflixTheme {
                // Your UI content
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
    }
}

object UserSession {
    var username: String? = null
}

data class LoginResponse(
    val message: String
)

// API Service
interface MediaApiService {
    @GET("media/category/{category}")
    suspend fun getMediaByCategory(@Path("category") category: String): List<Media>

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
    val passwordHash: String
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
    NavHost(navController, startDestination = "login") { // Start destination is now "login"
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

// Category Grid
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
                        val response = mediaApiService.loginUser(User(username, password))
                        if (response.isSuccessful) {
                            val loginResponse = response.body()
                            if (loginResponse?.message == "Login successful!") {
                                UserSession.username = username
                                navController.navigate("categories")
                            } else {
                                errorMessage = "Login failed"
                            }
                        } else {
                            errorMessage = "Login failed"
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
                            val response = mediaApiService.registerUser(User(username, password))
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
                    val response = mediaApiService.getMediaByCategory(categoryName)
                    mediaList.clear()
                    mediaList.addAll(response)
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
                setMediaItem(MediaItem.fromUri(Uri.parse(localFilePath)))
            } else if (!videoUrl.isNullOrBlank()) {
                setMediaItem(MediaItem.fromUri(videoUrl))
            }
            prepare()
            playWhenReady = true
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

        if (!fileExists && controlsVisible.value) {
            FloatingActionButton(
                onClick = {
                    coroutineScope.launch {
                        downloadInProgress.value = true
                        try {
                            val result = downloadVideo(context, videoUrl ?: "", localFilePath)
                            if (result) {
                                exoPlayer.setMediaItem(MediaItem.fromUri(Uri.parse(localFilePath)))
                                exoPlayer.prepare()
                                fileExists = true
                            }
                        } catch (e: Exception) {
                            Log.e("PlayerScreen", "Error downloading video: ${e.message}")
                        } finally {
                            downloadInProgress.value = false
                        }
                    }
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp),
                containerColor = Color.Black
            ) {
                if (downloadInProgress.value) {
                    CircularProgressIndicator(color = Color.White)
                } else {
                    Icon(Icons.Default.Download, contentDescription = "Download Video", tint = Color.White)
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