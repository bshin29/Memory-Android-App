package com.bshin.memorygame.models

//list out all attributes of a memory card for this game
data class MemoryCard(

    //val = cant be changed, var = can be changed
    val identifier:Int,
    val imageUrl:String?=null,
    var isFaceUp:Boolean=false,
    var isMatched:Boolean=false

)