package com.example.mediapipeapp.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

@Composable
fun AppNavigation(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    NavHost(
        navController = navController, 
        startDestination = "home",
        modifier = modifier
    ) {
        composable("home") {
            HomeScreen(
                onProtocolHelp = { promptName ->
                    navController.navigate("chat/$promptName")
                },
                onDataStructuring = { promptName ->
                    navController.navigate("chat/$promptName")
                },
                onGeneralChat = {
                    navController.navigate("chat/default")
                }
            )
        }
        composable(
            "chat/{promptName}",
            arguments = listOf(navArgument("promptName") { type = NavType.StringType })
        ) { backStackEntry ->
            val promptName = backStackEntry.arguments?.getString("promptName") ?: "default"
            LlmChatRoute(
                promptName = promptName,
                onShowHistory = {
                    navController.navigate("history")
                }
            )
        }
        composable(
            "chat/{promptName}/{conversationId}",
            arguments = listOf(
                navArgument("promptName") { type = NavType.StringType },
                navArgument("conversationId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val promptName = backStackEntry.arguments?.getString("promptName") ?: "default"
            val conversationId = backStackEntry.arguments?.getLong("conversationId") ?: -1L
            LlmChatRoute(
                promptName = promptName,
                conversationId = if (conversationId != -1L) conversationId else null,
                onShowHistory = {
                    navController.navigate("history")
                }
            )
        }
        composable("history") {
            ChatHistoryRoute(
                onConversationSelected = { conversationId ->
                    // Navigate back to chat with selected conversation
                    navController.navigate("chat/default/$conversationId") {
                        popUpTo("history") { inclusive = true }
                    }
                },
                onNewChat = {
                    navController.navigate("chat/default") {
                        popUpTo("history") { inclusive = true }
                    }
                },
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
