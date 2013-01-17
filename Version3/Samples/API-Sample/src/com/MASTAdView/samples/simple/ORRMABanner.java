package com.MASTAdView.samples.simple;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.WindowManager;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;

import com.MASTAdView.MASTAdLog;
import com.MASTAdView.MASTAdRequest;
import com.MASTAdView.MASTAdView;
import com.MASTAdView.samples.R;

public class ORRMABanner extends Activity {
	private MASTAdView adserverView;
	private LinearLayout linearLayout;
	private EditText inpSite;
	private EditText inpZone;
	private Button btnRefresh;
	private int site = 30299; // was 19829;
	private int zone = 156037; // aron: 152280; // crisp mr2 152600; // was 98463;
	
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
        setContentView(R.layout.main);
        
        linearLayout = (LinearLayout) findViewById(R.id.frameAdContent);
        inpSite = (EditText) findViewById(R.id.inpSite);
        inpSite.setText(String.valueOf(site));
        inpZone = (EditText) findViewById(R.id.inpZone);
        inpZone.setText(String.valueOf(zone));
        btnRefresh = (Button) findViewById(R.id.btnRefresh);
        btnRefresh.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				try {
					site = Integer.parseInt(inpSite.getText().toString());
			        zone = Integer.parseInt(inpZone.getText().toString());
			        adserverView.getAdRequest().setProperty(MASTAdRequest.parameter_site, site);
			        adserverView.getAdRequest().setProperty(MASTAdRequest.parameter_zone, zone);
					adserverView.update();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
        
        adserverView = new MASTAdView(this, site, zone);
        
        adserverView.setId(1);
        setAdLayoutParams();
        linearLayout.addView(adserverView);
        adserverView.getAdLog().setLogLevel(MASTAdLog.LOG_LEVEL_DEBUG);
        //adserverView.setLocationDetection(true, null, null);
        adserverView.setUpdateTime(0);
		adserverView.update();
        
        LinearLayout frameMain = (LinearLayout) findViewById(R.id.frameMain);
        BitmapDrawable background = (BitmapDrawable)getResources().getDrawable(R.drawable.repeat_bg);
        background.setTileModeXY(Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
        frameMain.setBackgroundDrawable(background);
    }
    
	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		//setAdLayoutParams();
		//adserverView.update();
	}
		
	private void setAdLayoutParams() {
		WindowManager windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
		DisplayMetrics metrics = new DisplayMetrics();
		windowManager.getDefaultDisplay().getMetrics(metrics);

		int height = 150;
		//int width = metrics.widthPixels;
		int width = LayoutParams.FILL_PARENT;

		ViewGroup.LayoutParams lp = adserverView.getLayoutParams();
		if (lp == null) {
			//lp = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT, height);
			lp = new ViewGroup.LayoutParams(width, height);
			adserverView.setLayoutParams(lp);
		}
		
		// Min size can be useful, but if you don't have ads large enough for all devices, it
		// can result in no ad being shown, so use it sparingly.
        //adserverView.setMinSizeX(metrics.widthPixels);
        //adserverView.setMinSizeY(height);
		
        //adserverView.setMaxSizeX(metrics.widthPixels);
		adserverView.getAdRequest().setProperty(MASTAdRequest.parameter_size_x, metrics.widthPixels);
        adserverView.getAdRequest().setProperty(MASTAdRequest.parameter_size_y, height);
        
        
        // Test custom viewport code suggested by Celtra
        //adserverView.setInjectionHeaderCode("<meta name=\"viewport\" content=\"initial-scale=1.0, minimum-scale=1.0, maximum-scale=1.0, user-scalable=no\" />");
        
        // Another viewport test
        //adserverView.setInjectionHeaderCode("<meta name=\"viewport\" content=\"target-densitydpi=device-dpi, width=ADVIEWWIDTH\" />");
        
        
		adserverView.requestLayout();
	}
	
}