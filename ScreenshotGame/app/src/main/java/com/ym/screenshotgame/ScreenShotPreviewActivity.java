package com.ym.screenshotgame;

import android.app.Activity;
import android.graphics.Bitmap;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;

import java.io.File;

public class ScreenShotPreviewActivity extends Activity {

    private ImageView ivPreview;
    private Button btnShare;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_screen_shot_preview);

        ivPreview = (ImageView)this.findViewById(R.id.iv_preview_image);
        btnShare = (Button)this.findViewById(R.id.btn_share);


        final String imgPath = this.getIntent().getStringExtra("imgPath");
        if(imgPath==null || imgPath==""){
            finish();
        }
        boolean b = (new File(imgPath)).exists();
        Log.d("PreviewActivity", "图片是否存在: " + b);
        Bitmap thumbnailBitmap = BitmapTools.makeThumbnailFromScreenshot(imgPath);
        Log.d("PreviewActivity", "thumnailBitmap: " + thumbnailBitmap);
        if(thumbnailBitmap != null) {
            ivPreview.setImageBitmap(thumbnailBitmap);
        } else {
            this.finish();
        }

        final Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                handler.removeCallbacks(this);
                finish();
            }
        },500);

        //分享按钮的监听
        btnShare.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Bitmap bitmap = BitmapTools.getScreenshotBitmap(imgPath);
                //todo：吊起分享渠道进行分享
            }
        });

    }
}
