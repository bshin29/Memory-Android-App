package com.bshin.memorygame

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.RecyclerView
import com.bshin.memorygame.models.BoardSize
import com.bshin.memorygame.models.MemoryCard
import com.squareup.picasso.Picasso
import kotlin.math.min

class MemoryBoardAdapter(
    private val context: Context,
    private val boardSize: BoardSize,
    private val cards: List<MemoryCard>,
    private val cardClickListener: CardClickListener
) :
    RecyclerView.Adapter<MemoryBoardAdapter.ViewHolder>() {

    companion object { //a companion object is like a static java variable
        private const val MARGIN_SIZE = 10
        private const val TAG = "MemoryBoardAdapter"
    }

    //Changing state of memory card --> notify main activity to notify MemoryGame Class
    interface CardClickListener{
        fun onCardClicked(position:Int)
    }

    //create 1 view of our recycler view
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val cardWidth = parent.width/boardSize.getWidth() - 2*MARGIN_SIZE
        val cardHeight = parent.height/boardSize.getHeight() - 2*MARGIN_SIZE
        val cardSideLength = min(cardWidth, cardHeight)

        val view = LayoutInflater.from(context).inflate(R.layout.memory_card,parent,false)
        val layoutParams = view.findViewById<CardView>(R.id.cardView).layoutParams as ViewGroup.MarginLayoutParams
        layoutParams.width = cardSideLength
        layoutParams.height = cardSideLength
        layoutParams.setMargins(MARGIN_SIZE, MARGIN_SIZE, MARGIN_SIZE, MARGIN_SIZE)
        return ViewHolder(view)
    }

    //how many elements are in our recycler view
    override fun getItemCount() = boardSize.numCards

    //takes data at position to the holder container
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(position)
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageButton = itemView.findViewById<ImageButton>(R.id.imageButton)

        fun bind(position: Int) {
            val memCard = cards[position]

            //render image from URL
            if(memCard.isFaceUp){
                if(memCard.imageUrl != null){
                    Picasso.get().load(memCard.imageUrl).placeholder(R.drawable.ic_image).into(imageButton)
                }
                else{
                    imageButton.setImageResource(memCard.identifier)
                }
            }
            else{
                imageButton.setImageResource(R.drawable.ic_launcher_background)
            }

            //This section of code is used to grey out the background color of the image button when a match is found
            imageButton.alpha = if(memCard.isMatched) .4f else 1.0f //alpha = transparency of image
            val colorStateList = if(memCard.isMatched) ContextCompat.getColorStateList(context,R.color.color_gray) else null
            ViewCompat.setBackgroundTintList(imageButton,colorStateList)

            imageButton.setOnClickListener{
                Log.i(TAG,"Clicked on position $position")
                cardClickListener.onCardClicked(position)
            }
        }
    }

}
