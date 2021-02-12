package com.bshin.memorygame

import android.animation.ArgbEvaluator
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bshin.memorygame.models.BoardSize
import com.bshin.memorygame.models.MemoryGame
import com.bshin.memorygame.models.UserImageList
import com.bshin.memorygame.utils.EXTRA_BOARD_SIZE
import com.bshin.memorygame.utils.EXTRA_GAME_NAME
import com.github.jinatonic.confetti.CommonConfetti
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.squareup.picasso.Picasso

class MainActivity : AppCompatActivity() {

    companion object{
        private const val TAG = "MainActivity"
        private const val CREATE_REQUEST_CODE = 248
    }

    private lateinit var memoryGame: MemoryGame
    private lateinit var adapter:MemoryBoardAdapter
    private lateinit var clRoot:CoordinatorLayout
    private lateinit var rvBoard:RecyclerView //why late init var? bc we these variables will be set later
    private lateinit var tvNumMoves: TextView
    private lateinit var tvNumPairs:TextView

    private val db = Firebase.firestore
    private var customGameImages:List<String>? = null
    private var gameName:String? = null
    private var boardSize: BoardSize =BoardSize.EASY

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        clRoot = findViewById(R.id.clRoot)
        rvBoard = findViewById(R.id.rvBoard)
        tvNumMoves = findViewById(R.id.tvNumMoves)
        tvNumPairs = findViewById(R.id.tvNumPairs)

//        //helps improve efficiency
//        val intent = Intent(this,CreateActivity::class.java)
//        intent.putExtra(EXTRA_BOARD_SIZE,BoardSize.EASY)
//        startActivity(intent)

        setUpBoard()

    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main,menu)
        return true //notify if user taps on the "refresh" menu item
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when(item.itemId){
            //When refresh menu item selected
            R.id.mi_refresh->{
                if(memoryGame.getNumMoves()>0 && !memoryGame.haveWonGame()){
                    showAlertDialog("Quit your current game?",null,View.OnClickListener{setUpBoard()})
                }
                else{
                    setUpBoard()
                }
            }

            //When new size menu item selected
            R.id.mi_new_size->{
                showNewSizeDialog()
                return true
            }

            //When custom game is selected
            R.id.mi_custom->{
                showCreationDialog()
                return true
            }

            //When download game is selected
            R.id.mi_download->{
                showDownloadDialog()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?){
        if(requestCode == CREATE_REQUEST_CODE && resultCode==Activity.RESULT_OK){
            val customGameName = data?.getStringExtra(EXTRA_GAME_NAME)
            if(customGameName == null){
                //something has gone wrong
                return
            }
            downloadGame(customGameName) //contact firestore and get image URLS for your custom game
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    //Get custom game images from Firebase
    private fun downloadGame(customGameName: String) {
        //the "document" from firebase is a list of image urls in our storage
        db.collection("games").document(customGameName).get().addOnSuccessListener { document->
            val userImageList = document.toObject(UserImageList::class.java) //turn document into Kotlin data class
            if(userImageList?.images ==null){ //if user image list is null
                Snackbar.make(clRoot,"Sorry, we couldn't find the game '${customGameName}'",Snackbar.LENGTH_LONG).show()
                return@addOnSuccessListener
            }

            //have found a game successfully
            val numCards = userImageList.images.size * 2
            boardSize = BoardSize.getByValue(numCards)
            gameName = customGameName
            customGameImages = userImageList.images

            //pre-fetch all images via Picasso
            for(imageUrl in userImageList.images){
                Picasso.get().load(imageUrl).fetch()
            }

            Snackbar.make(clRoot,"You're now playing '$customGameName", Snackbar.LENGTH_LONG).show()

            gameName = customGameName
            setUpBoard()

        }.addOnFailureListener{exception->

        }
    }


    private fun showCreationDialog() {
        val boardSizeView = LayoutInflater.from(this).inflate(R.layout.dialog_board_size,null)
        val radioGroupSize = boardSizeView.findViewById<RadioGroup>(R.id.radioGroup)

        showAlertDialog("Create your own memory board",boardSizeView,View.OnClickListener {
            //set a new board difficulty
            val desiredBoardSize = when(radioGroupSize.checkedRadioButtonId){
                R.id.rbEasy -> BoardSize.EASY
                R.id.rbMedium -> BoardSize.MEDIUM
                else -> BoardSize.HARD
            }
            //Navigate user to new view
            val intent = Intent(this,CreateActivity::class.java)
            intent.putExtra(EXTRA_BOARD_SIZE, desiredBoardSize)
            startActivityForResult(intent, CREATE_REQUEST_CODE)
        })
    }

    private fun showNewSizeDialog() {
        val boardSizeView = LayoutInflater.from(this).inflate(R.layout.dialog_board_size,null)
        val radioGroupSize = boardSizeView.findViewById<RadioGroup>(R.id.radioGroup)

        //This automatically selects the radio button based on difficulty
        when(boardSize){
            BoardSize.EASY -> radioGroupSize.check(R.id.rbEasy)
            BoardSize.MEDIUM -> radioGroupSize.check(R.id.rbMedium)
            BoardSize.HARD -> radioGroupSize.check(R.id.rbHard)
        }
        showAlertDialog("Choose difficulty",boardSizeView,View.OnClickListener {
            //set a new board difficulty
            boardSize = when(radioGroupSize.checkedRadioButtonId){
                R.id.rbEasy -> BoardSize.EASY
                R.id.rbMedium -> BoardSize.MEDIUM
                else -> BoardSize.HARD
            }
            gameName = null
            customGameImages=null
            setUpBoard()
        })
    }

    private fun showAlertDialog(title:String, view: View?, positiveClickListener:View.OnClickListener) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(view)
            .setNegativeButton("Cancel",null)
            .setPositiveButton("OK"){_,_->
                positiveClickListener.onClick(null)
            }.show()
    }

    private fun showDownloadDialog() {
        val boardDownloadView = LayoutInflater.from(this).inflate(R.layout.dialog_download_board,null)
        showAlertDialog("Fetch memory game",boardDownloadView,View.OnClickListener {
            //grab text of the game name that the user wants to download
            val etDownloadGame = boardDownloadView.findViewById<EditText>(R.id.etDownloadGame)
            val gameToDownload=etDownloadGame.text.toString().trim()
            downloadGame(gameToDownload)
        })
    }

    private fun setUpBoard(){
        //custom game name
        supportActionBar?.title = gameName ?: getString(R.string.app_name)

        when(boardSize){
            BoardSize.EASY -> {
                tvNumMoves.text = "Easy: 4 x 2"
                tvNumPairs.text = "Pairs: 0 / 4"
            }
            BoardSize.MEDIUM -> {
                tvNumMoves.text = "Medium: 6 x 3"
                tvNumPairs.text = "Pairs: 0 / 9"
            }
            BoardSize.HARD -> {
                tvNumMoves.text = "Hard: 6 x 4"
                tvNumPairs.text = "Pairs: 0 / 12"
            }
        }

        tvNumPairs.setTextColor(ContextCompat.getColor(this,R.color.color_progress_none))
        memoryGame = MemoryGame(boardSize,customGameImages)
        adapter = MemoryBoardAdapter(this,boardSize, memoryGame.cards, object:MemoryBoardAdapter.CardClickListener{
            override fun onCardClicked(position:Int){
                updateGameWithFlip(position)
            }
        })
        rvBoard.adapter = adapter
        rvBoard.setHasFixedSize(true)
        rvBoard.layoutManager = GridLayoutManager(this, boardSize.getWidth())
    }

    private fun updateGameWithFlip(position:Int){
        //there are 2 error cases that occur:
        //1. if game has been won
        if(memoryGame.haveWonGame()){
            //Alert user of invalid move
            Snackbar.make(clRoot, "You have already won the game!",Snackbar.LENGTH_LONG).show()
            return
        }

        //2. memory card is already face up
        if(memoryGame.isCardFaceUp(position)){
            Snackbar.make(clRoot, "Invalid move",Snackbar.LENGTH_SHORT).show()
            return
        }

        //After error check passes, flip over card
        if(memoryGame.flipCard(position)){
            Log.i(TAG,"Found a match! Num pairs found: ${memoryGame.numPairsFound}")

            //Sets text with color represnting progress
            val color = ArgbEvaluator().evaluate(
                memoryGame.numPairsFound.toFloat()/boardSize.getNumPairs(),
                ContextCompat.getColor(this,R.color.color_progress_none),
                ContextCompat.getColor(this,R.color.color_progress_full)
            ) as Int

            tvNumPairs.setTextColor(color)
            tvNumPairs.text = "Pairs: ${memoryGame.numPairsFound} / ${boardSize.getNumPairs()}"
            if(memoryGame.haveWonGame()){
                Snackbar.make(clRoot, "You have won!",Snackbar.LENGTH_LONG).show()
                CommonConfetti.rainingConfetti(clRoot,intArrayOf(Color.YELLOW,Color.GREEN,Color.MAGENTA)).oneShot()
            }
        }
        tvNumMoves.text = "Moves: ${memoryGame.getNumMoves()}"
        adapter.notifyDataSetChanged()
    }
}