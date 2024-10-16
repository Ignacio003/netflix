package com.example.netflix

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Person // Icono de perfil
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.netflix.ui.theme.NetflixTheme

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
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Go back", tint = Color.White)
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
fun CategoryGrid(navController: NavController) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier
            .padding(20.dp)
            .background(Color.Black),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        val categories = listOf("Series", "Films", "Documentaries", "Kids")
        items(categories) { category ->
            CategoryBox(categoryName = category, onClick = {
                navController.navigate("category/$category")
            })
        }
    }
}

@Composable
fun CategoryBox(categoryName: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .background(Color(0xFF1B0033))
            .size(180.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Image(
            painter = painterResource(id = R.drawable.series),
            contentDescription = null,
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
        composable("profile") { //
            ProfileScreen(navController = navController)
        }
    }
}

@Composable
fun CategoryScreen(navController: NavController, categoryName: String?) {
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

        Text(text = "Welcome to $categoryName", color = Color.White)
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

@Preview(showBackground = true)
@Composable
fun SuperiorPreview() {
    NetflixTheme {
        Column(
            modifier = Modifier.background(Color.Black)
        ) {
            SuperiorPart("Netflix+")
            CategoryGrid(navController = rememberNavController())
        }
    }
}
