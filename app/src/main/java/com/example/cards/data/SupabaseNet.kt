package com.example.cards.data

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime

object SupabaseNet {
    val client = createSupabaseClient(
        supabaseUrl = "https://zbkueaitzocaopoudbxt.supabase.co",
        supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Inpia3VlYWl0em9jYW9wb3VkYnh0Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzUxMjIwMjQsImV4cCI6MjA5MDY5ODAyNH0.cTVMBp7vmsZ8gLYsse4XHcJegt0NA2n4fgAox2A5PZk"
    ) {
        install(Realtime)
        install(Postgrest)
    }
}