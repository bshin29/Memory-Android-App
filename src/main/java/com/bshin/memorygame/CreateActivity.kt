package com.bshin.memorygame

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bshin.memorygame.models.BoardSize
import com.bshin.memorygame.utils.*
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import java.io.ByteArrayOutputStream


class CreateActivity : AppCompatActivity() {

    companion object{
        private const val PICK_PHOTO_CODE = 577
        private const val READ_EXTERNAL_PHOTOS_CODE = 248
        private const val READ_PHOTOS_PERMISSION = android.Manifest.permission.READ_EXTERNAL_STORAGE
        private const val TAG = "CreateActivity"
        private const val MIN_GAME_NAME_LENGTH = 3
        private const val MAX_GAME_NAME_LENGTH = 14

    }

    private lateinit var rvImagePicker: RecyclerView
    private lateinit var etGameName: EditText
    private lateinit var btnSave: Button
    private lateinit var boardSize: BoardSize
    private lateinit var adapter:ImagePickerAdapter
    private lateinit var pbUploading: ProgressBar

    private var numImagesRequired = -1
    private val chosenImagesUris = mutableListOf<Uri>()
    private val storage = Firebase.storage
    private val db = Firebase.firestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create)

        //Get widgets from the views
        rvImagePicker = findViewById(R.id.rvImagePicker)
        etGameName = findViewById(R.id.etGameName)
        btnSave = findViewById(R.id.btnSave)
        pbUploading = findViewById(R.id.pbUploading)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        boardSize = intent.getSerializableExtra(EXTRA_BOARD_SIZE) as BoardSize
        numImagesRequired = boardSize.getNumPairs()
        supportActionBar?.title = "Choose images (0 / $numImagesRequired)"

        //upload custom images
        btnSave.setOnClickListener{
            saveDataToFirebase()
        }

        //Name of custom game
        etGameName.filters = arrayOf(InputFilter.LengthFilter(MAX_GAME_NAME_LENGTH))
        etGameName.addTextChangedListener(object: TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                btnSave.isEnabled = shouldEnableSaveButton()
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        adapter = ImagePickerAdapter(this,chosenImagesUris,boardSize, object:ImagePickerAdapter.ImageClickListener{
            override fun onPlaceHolderClicked() {
                if(isPermissionGranted(this@CreateActivity, READ_PHOTOS_PERMISSION)){
                    launchIntentForPhotos()
                }
                else{
                    requestPermission(this@CreateActivity, READ_PHOTOS_PERMISSION, READ_EXTERNAL_PHOTOS_CODE)
                }
            }
        })
        rvImagePicker.adapter = adapter
        rvImagePicker.setHasFixedSize(true)
        rvImagePicker.layoutManager = GridLayoutManager(this,boardSize.getWidth())
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode== READ_EXTERNAL_PHOTOS_CODE){
            if(grantResults.isNotEmpty() && grantResults[0]==PackageManager.PERMISSION_GRANTED){
                launchIntentForPhotos()
            }
            else{
                Toast.makeText(this,
                    "In order to create a custom game, you must give access to your images", Toast.LENGTH_LONG).show()
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    //Getting images from user's phone
    private fun launchIntentForPhotos(){
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true)
        startActivityForResult(Intent.createChooser(intent,"Choose photos"),PICK_PHOTO_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        //for all cases where we wont find valid photo data
        if(requestCode != PICK_PHOTO_CODE || resultCode != Activity.RESULT_OK || data==null ){
            Log.w(TAG,"Did not get data back from the launched activity, user cancelled flow")
            return
        }
        //we now have valid data inside the data intent
        val selectedUri = data.data //user has selected only 1 photo
        val clipData = data.clipData //user has selected many photos
        if(clipData!=null){ //start with clipdata
            Log.i(TAG, "clipData numImages ${clipData.itemCount}:$clipData")
            for(i in 0 until clipData.itemCount){
                val clipItem = clipData.getItemAt(i)
                if(chosenImagesUris.size<numImagesRequired){ //do we still need to add images to the list?
                    chosenImagesUris.add(clipItem.uri) //add this URI to the list
                }
            }
        }
        //if we get here, means user selected only 1 photo
        else if(selectedUri!=null){
            Log.i(TAG,"data: $selectedUri")
            chosenImagesUris.add(selectedUri)
        }
        adapter.notifyDataSetChanged()
        supportActionBar?.title = "Choose images ( ${chosenImagesUris.size} / $numImagesRequired )"
        btnSave.isEnabled = shouldEnableSaveButton()
    }

    private fun shouldEnableSaveButton():Boolean{
        //check if we should enable save button or not
        if(chosenImagesUris.size != numImagesRequired){
            return false
        }
        if (etGameName.text.isBlank() || etGameName.text.length < MIN_GAME_NAME_LENGTH){
            return false
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if(item.itemId == android.R.id.home){
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    //uploading images to firebase
    private fun saveDataToFirebase() {
        //when user presses save button, dont want them to press again
        btnSave.isEnabled = false

        //get name inputed by user
        val customGameName = etGameName.text.toString()
        //check if there is a firestore document with the same game name
        db.collection("games").document(customGameName).get().addOnSuccessListener { document ->
            if(document!=null && document.data!=null){
                AlertDialog.Builder(this)
                    .setTitle("This game name has already been taken")
                    .setMessage("A game already exists with the name '$customGameName'. Please try again.")
                    .setPositiveButton("OK",null)
                    .show()
                btnSave.isEnabled = true //if we get an error, let them save
            }
            else{
                handleImageUploading(customGameName)
            }
        }.addOnFailureListener{ exception ->
            Toast.makeText(this,"Encountered an error",Toast.LENGTH_SHORT).show()
            btnSave.isEnabled = true
        }

    }

    private fun handleImageUploading(gameName:String ) {
        pbUploading.visibility = View.VISIBLE
        var didEncounterError = false
        val uploadedImageUrls = mutableListOf<String>()

        for((index,photoUri) in chosenImagesUris.withIndex()){
            val imageByteArray = getImageByteArray(photoUri)
            val filePath = "images/$gameName/${System.currentTimeMillis()}-${index}.jpg"
            val photoReference = storage.reference.child(filePath) //location to save image

            //uploading to firebase
            photoReference.putBytes(imageByteArray).continueWithTask{ //wait for task to finish
                    photoUploadTask-> Log.i(TAG, "Uploaded bytes: ${photoUploadTask.result?.bytesTransferred}")
                photoReference.downloadUrl //task is done when we get the download URL for the image
            }.addOnCompleteListener{downloadUrlTask-> //waiting for download link to be done
                if(!downloadUrlTask.isSuccessful){ //do we have the link? NO
                    Toast.makeText(this, "Failed to upload image",Toast.LENGTH_SHORT).show()
                    didEncounterError = true
                    return@addOnCompleteListener //no point in continuing
                }
                if(didEncounterError){ //some image has failed to upload
                    pbUploading.visibility = View.GONE
                    return@addOnCompleteListener
                }
                //get the download url if there are no errors
                val downloadUrl = downloadUrlTask.result.toString()
                uploadedImageUrls.add(downloadUrl)
                pbUploading.progress = uploadedImageUrls.size*100/chosenImagesUris.size

                //we are done when all user-selected images are uploaded to firebase
                if(uploadedImageUrls.size == chosenImagesUris.size){
                    handleAllImagesUploaded(gameName,uploadedImageUrls)
                }
            }
        }
    }

    private fun handleAllImagesUploaded(gameName: String, imageUrls: MutableList<String>) {
        db.collection("games")
            .document(gameName)
            .set(mapOf("images" to imageUrls))
            .addOnCompleteListener{gameCreationTask->
                pbUploading.visibility = View.GONE
                if(!gameCreationTask.isSuccessful){
                    Toast.makeText(this,"Failed game creation", Toast.LENGTH_SHORT).show()
                    return@addOnCompleteListener
                }
                //succesfully uploaded and created game
                AlertDialog.Builder(this)
                    .setTitle("Upload complete! Let's play your game '$gameName'")
                    .setPositiveButton("OK"){_,_->
                        val resultData = Intent()
                        resultData.putExtra(EXTRA_GAME_NAME,gameName)
                        setResult(Activity.RESULT_OK,resultData)
                        finish()
                    }.show()
            }
    }

    //used to downgrade the quality of all selected images
    private fun getImageByteArray(photoUri: Uri): ByteArray {
        val originalBitmap = if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P){
            val source = ImageDecoder.createSource(contentResolver,photoUri)
            ImageDecoder.decodeBitmap(source)
        }
        else{
            MediaStore.Images.Media.getBitmap(contentResolver,photoUri)
        }
        val scaledBitmap = BitmapScaler.scaleToFitHeight(originalBitmap,250)

        val byteOutputStream = ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG,60,byteOutputStream)
        return byteOutputStream.toByteArray()
    }
}