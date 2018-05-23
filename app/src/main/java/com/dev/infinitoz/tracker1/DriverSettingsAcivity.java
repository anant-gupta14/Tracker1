package com.dev.infinitoz.tracker1;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import com.bumptech.glide.Glide;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class DriverSettingsAcivity extends AppCompatActivity {

    private EditText mNameField, mPhoneFields,mCarfield;
    private Button mBack, mconfirm;

    private FirebaseAuth mAuth;
    private DatabaseReference mDriverDatabase;

    private  String userID, mName, mPhone, mProfileImageUrl,mCar;


    private ImageView mProfileImage;

    private Uri resultUri;

    private String mService;

    private RadioGroup mRadioGroup;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_driver_settings_acivity);
        mNameField = findViewById(R.id.name);
        mPhoneFields = findViewById(R.id.phone);
        mBack = findViewById(R.id.back);
        mconfirm = findViewById(R.id.confirm);

        mProfileImage = findViewById(R.id.profileImage);
        mCarfield = findViewById(R.id.car);
        mRadioGroup = findViewById(R.id.radioGroup);

        mAuth = FirebaseAuth.getInstance();
        userID = mAuth.getCurrentUser().getUid();
        mDriverDatabase = FirebaseDatabase.getInstance().getReference().child("Users").child("Drivers").child(userID);

        getUserInfo();

        mProfileImage.setOnClickListener(v->{
            Intent intent = new Intent(Intent.ACTION_PICK);
            intent.setType("image/*");
            startActivityForResult(intent,1);

        });
        mconfirm.setOnClickListener(v->{saveUserInformation();});
        mBack.setOnClickListener(v->{finish();return;});

    }

    private void saveUserInformation(){
        mName = mNameField.getText().toString();
        mPhone = mPhoneFields.getText().toString();
        mCar = mCarfield.getText().toString();

        int selectId = mRadioGroup.getCheckedRadioButtonId();

        final RadioButton radioButton = findViewById(selectId);

        if(radioButton.getText() == null){
            return;
        }

        mService = radioButton.getText().toString();

        Map<String,Object> userInfo = new HashMap<>();
        userInfo.put("name",mName);
        userInfo.put("phone",mPhone);
        userInfo.put("car",mCar);
        userInfo.put("service",mService);
        mDriverDatabase.updateChildren(userInfo);

        if(resultUri != null){
            StorageReference filePath = FirebaseStorage.getInstance().getReference().child("profile_images").child(userID);
            Bitmap bitmap = null;
            try{
                bitmap = MediaStore.Images.Media.getBitmap(getApplication().getContentResolver(),resultUri);

            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG,20,baos);
            byte[] data = baos.toByteArray();
            UploadTask uploadTask = filePath.putBytes(data);

            uploadTask.addOnFailureListener(e->
            {
                finish();
                return;
            });

            uploadTask.addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
                @Override
                public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                    Uri downloadUrl = taskSnapshot.getDownloadUrl();

                    Map newImage = new HashMap();
                    newImage.put("profileImageUrl",downloadUrl.toString());
                    mDriverDatabase.updateChildren(newImage);
                    finish();
                    return;
                }
            });

        }
        else {
            finish();
        }

    }
    private void getUserInfo(){
        mDriverDatabase.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if(dataSnapshot.exists() && dataSnapshot.getChildrenCount()>0){
                    Map<String,Object> map = (Map<String, Object>) dataSnapshot.getValue();
                    if(map.get("name") != null){
                        mName = map.get("name").toString();
                        mNameField.setText(mName);
                    }
                    if(map.get("phone") != null){
                        mPhone = map.get("phone").toString();
                        mPhoneFields.setText(mPhone);
                    }
                    if(map.get("car") != null){
                        mCar = map.get("car").toString();
                        mCarfield.setText(mCar);
                    }
                    if(map.get("service") != null){
                        mService = map.get("service").toString();
                        switch (mService){
                            case "UberX":
                                mRadioGroup.check(R.id.uberX);
                                break;
                            case "UberBlack":
                                mRadioGroup.check(R.id.uberBlack);
                                break;
                            case "UberXl":
                                mRadioGroup.check(R.id.uberXl);
                                break;

                        }
                    }
                    if(map.get("profileImageUrl") != null){
                        mProfileImageUrl = map.get("profileImageUrl").toString();
                        Glide.with(getApplicationContext()).load(mProfileImageUrl).into(mProfileImage);
                    }
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode == 1 && resultCode == Activity.RESULT_OK){
            final Uri imageUri = data.getData();
            resultUri = imageUri;
            mProfileImage.setImageURI(resultUri);
        }
    }
}
