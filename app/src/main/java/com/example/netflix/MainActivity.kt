package com.example.netflix

import android.os.Bundle
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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

// MainActivity
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
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
    }
}

// API Service
interface MediaApiService {
    @GET("media/category/{category}")
    suspend fun getMediaByCategory(@Path("category") category: String): List<Media>
}

val retrofit = Retrofit.Builder()
    .baseUrl("http://34.175.133.0:8080/")
    .addConverterFactory(GsonConverterFactory.create())
    .build()

val mediaApiService: MediaApiService = retrofit.create(MediaApiService::class.java)


data class Media(
    val mediaId: Int,
    val title: String,
    val description: String,
    val highResUrl: String,
    val lowResUrl: String,
    val category: String
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

// Navigation
@Composable
fun NetflixNavHost(navController: NavHostController) {
    NavHost(navController, startDestination = "categories") {
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
                    MediaBox(media)
                }
            }
        }
    }
}

@OptIn(ExperimentalCoilApi::class)
@Composable
fun MediaBox(media: Media) {
    Column(
        modifier = Modifier
            .background(Color(0xFF1B0033))
            .size(180.dp)
            .clickable {
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
fun ProfileScreen(navController: NavController) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        SuperiorPart(
            name = "Profile",
            showBackButton = true,
            onBackClick = {
                if (navController.previousBackStackEntry != null) {
                    navController.popBackStack()
                }
            }
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "User Profile", color = Color.White)
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "Email: user@example.com", color = Color.White)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "Password: ••••••••", color = Color.White)
    }
}
