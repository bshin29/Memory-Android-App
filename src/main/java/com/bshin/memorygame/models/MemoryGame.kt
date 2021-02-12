package com.bshin.memorygame.models

import com.bshin.memorygame.utils.DEFAULT_ICONS

class MemoryGame(
    private val boardSize: BoardSize,
    private val customImages: List<String>?
){

    val cards: List<MemoryCard>
    var numPairsFound = 0

    private var numCardFlips = 0
    private var indexOfSingleSelectedCard:Int?=null


    init{
        if(customImages == null){ //using default icons
            val chosenImages = DEFAULT_ICONS.shuffled().take(boardSize.getNumPairs())
            val randomizedImages = (chosenImages + chosenImages).shuffled()
            cards = randomizedImages.map{ MemoryCard(it) } //for every element of randomized images, create a new memory card object
        }
        else{ //use custom game images
            val randomizedImages = (customImages+customImages).shuffled()
            cards = randomizedImages.map{ MemoryCard(it.hashCode(),it)}
        }

    }

    //This is the game logic
    fun flipCard(position: Int) : Boolean {
        numCardFlips++
        val card = cards[position]
        var foundMatch = false
        //we have 3 cases:
        //1. there were 0 cards flipped over --> just flip over that card
        //2. there was 1 card flipped over --> flip over + check if the two images match
        //3. there were 2 cards flipped over --> restore cards to face down, then flip this card over

        if (indexOfSingleSelectedCard==null){
            restoreCards()
            indexOfSingleSelectedCard = position
        }
        else{
            //exactly 1 card flipped over already
            //flip over card and check if the 2 flipped match
            foundMatch = checkForMatch(indexOfSingleSelectedCard!!,position)
            indexOfSingleSelectedCard=null
        }
        card.isFaceUp = !card.isFaceUp
        return foundMatch
    }

    private fun checkForMatch(position1:Int, position2:Int): Boolean{
        //if there is NOT match between the two cards
        if(cards[position1].identifier != cards[position2].identifier){
            return false
        }

        //if the cards DO match
        cards[position1].isMatched = true
        cards[position2].isMatched = true
        numPairsFound++
        return true
    }

    private fun restoreCards(){
        for(card in cards){
            if(!card.isMatched){
                card.isFaceUp = false
            }

        }
    }

    fun haveWonGame(): Boolean {
        return numPairsFound == boardSize.getNumPairs()
    }

    fun isCardFaceUp(position:Int):Boolean{
        return cards[position].isFaceUp
    }

    fun getNumMoves(): Int {
        return numCardFlips/2
    }

}