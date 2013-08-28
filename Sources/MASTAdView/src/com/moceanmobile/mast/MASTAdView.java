package com.moceanmobile.mast;

import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.json.JSONObject;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.provider.MediaStore;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.moceanmobile.mast.MASTAdViewDelegate.ActivityListener;
import com.moceanmobile.mast.MASTAdViewDelegate.FeatureSupportHandler;
import com.moceanmobile.mast.MASTAdViewDelegate.InternalBrowserListener;
import com.moceanmobile.mast.MASTAdViewDelegate.LogListener;
import com.moceanmobile.mast.MASTAdViewDelegate.RequestListener;
import com.moceanmobile.mast.MASTAdViewDelegate.RichMediaListener;
import com.moceanmobile.mast.mraid.Bridge;
import com.moceanmobile.mast.mraid.Consts;
import com.moceanmobile.mast.mraid.Consts.Feature;
import com.moceanmobile.mast.mraid.Consts.ForceOrientation;
import com.moceanmobile.mast.mraid.Consts.PlacementType;
import com.moceanmobile.mast.mraid.Consts.State;
import com.moceanmobile.mast.mraid.OrientationProperties;
import com.moceanmobile.mast.mraid.ResizeProperties;
import com.moceanmobile.mast.mraid.WebView;

public class MASTAdView extends ViewGroup
{
	public enum LogLevel
	{
		None,
		Error,
		Debug,
	}
	
	final private String sdkVersion = Defaults.SDK_VERSION;
	final private int CloseAreaSizeDp = 50;
	final private int OrientationReset = Short.MIN_VALUE;
	
	// User agent used for all requests
	private String userAgent = null;
	
	// Configuration
	private int zone = 0;
	private boolean test = false;
	private int updateInterval = 0;
	private String adNetworkURL = Defaults.AD_NETWORK_URL;
	private Map<String, String> adRequestParameters = new HashMap<String, String>();
	private boolean useInternalBrowser = false;
	private LogLevel logLevel = LogLevel.Error;
	private PlacementType placementType = PlacementType.Inline;
	
	// Ad containers (render ad content)
	private WebView webView = null;
	private TextView textView = null;
	private ImageView imageView = null;
	
	// Close button
	private boolean showCloseButton = false;
	private int closeButtonDelay = 0;
	private Drawable closeButtonCustomDrawable = null;
	private ScheduledFuture<?> closeButtonFuture = null;
	
	// Interstitial configuration
	private ExpandDialog interstitialDialog = null;
	private ScheduledFuture<?> interstitialDelayFuture = null;
	
	// MRAID support
	private Bridge mraidBridge = null;
	private boolean mraidBridgeInit = false;
	private Bridge.Handler mraidBridgeHandler = new MRAIDHandler();
	private ExpandDialog mraidExpandDialog = null;
	private RelativeLayout mraidResizeLayout = null;
	private View mraidResizeCloseArea = null;
	private boolean mraidTwoPartExpand = false;
	private Bridge mraidTwoPartBridge = null;
	private boolean mraidTwoPartBridgeInit = false;
	private WebView mraidTwoPartWebView = null;
	private int mraidOriginalOrientation = OrientationReset;
	
	// Handles WebView client callbacks for MRAID or other WebView based ads.
	private WebView.Handler webViewHandler = new WebViewHandler();
	
	// Updating
	private boolean updateOnLayout = false;
	private boolean deferredUpdate = false;
	private AdRequest adRequest = null;
	private AdDescriptor adDescriptor = null;
	private AdRequestHandler adRequestHandler = new AdRequestHandler();
	private ScheduledFuture<?> adUpdateIntervalFuture = null;
	
	// Tracking
	private boolean invokeTracking = false;
	
	// Internal browser
	private BrowserDialog browserDialog = null;
	
	// Location support
	private LocationManager locationManager = null;
	private LocationListener locationListener = null;
	
	// Delegates
	private ActivityListener activityListener;
	private FeatureSupportHandler featureSupportHandler;
	private InternalBrowserListener internalBrowserListener;
	private LogListener logListener;
	private RequestListener requestListener;
	private RichMediaListener richMediaListener;
	
	public MASTAdView(Context context)
	{
		super(context);
		init(false);
	}
	
	public MASTAdView(Context context, boolean interstitial)
	{
		super(context);
		init(interstitial);
	}
	
	public MASTAdView(Context context, AttributeSet attrs)
	{
		super(context, attrs);
		applyAttributeSet(attrs);
		init(false);
	}
	
	public MASTAdView(Context context, AttributeSet attrs, int defStyle)
	{
		super(context, attrs, defStyle);
		applyAttributeSet(attrs);
		init(false);
	}
	
	protected void init(boolean interstitial)
	{
		placementType = PlacementType.Inline;
		
		if (interstitial)
		{
			placementType = PlacementType.Interstitial;
			interstitialDialog = new ExpandDialog(getContext());
		}
		
		if (zone != 0)
		{
			updateOnLayout = true;
		}
		
		initUserAgent();
	}
	
	protected void applyAttributeSet(AttributeSet attrs)
	{
		setZone(attrs.getAttributeIntValue(null, MASTAdViewConstants.xml_layout_attribute_zone, zone));
		
		String strValue = attrs.getAttributeValue(null, MASTAdViewConstants.xml_layout_attribute_logLevel);
		if (TextUtils.isEmpty(strValue) == false)
		{
			setLogLevel(LogLevel.valueOf(strValue));
		}
	}
	
	private void updateOnLayout()
	{
		if (updateOnLayout)
		{
			updateOnLayout = false;

			update();
		}
	}
	
	private void initUserAgent()
	{
		if (TextUtils.isEmpty(userAgent))
		{
			userAgent = getWebView().getSettings().getUserAgentString();
			
			if (TextUtils.isEmpty(userAgent))
			{
				userAgent = Defaults.USER_AGENT;
			}
		}
	}
	
	public String getUserAgent()
	{
		return userAgent;
	}
	
	public boolean isInline()
	{
		if (placementType == PlacementType.Inline)
			return true;
		
		return false;
	}
	
	public boolean isInterstitial()
	{
		if (placementType == PlacementType.Interstitial)
			return true;
		
		return false;
	}
	
	public void setActivityListener(MASTAdViewDelegate.ActivityListener activityListener)
	{
		this.activityListener = activityListener;
	}
	
	public MASTAdViewDelegate.ActivityListener getActivityListener()
	{
		return activityListener;
	}
	
	public void setFeatureSupportHandler(MASTAdViewDelegate.FeatureSupportHandler featureSupportHandler)
	{
		this.featureSupportHandler = featureSupportHandler;
	}
	
	public MASTAdViewDelegate.FeatureSupportHandler getFeatureSupportHandler()
	{
		return featureSupportHandler;
	}
	
	public void setInternalBrowserListener(MASTAdViewDelegate.InternalBrowserListener internalBrowserListener)
	{
		this.internalBrowserListener = internalBrowserListener;
	}
	
	public MASTAdViewDelegate.InternalBrowserListener getInternalBrowserListener()
	{
		return internalBrowserListener;
	}
	
	public void setLogListener(MASTAdViewDelegate.LogListener logListener)
	{
		this.logListener = logListener;
	}
	
	public MASTAdViewDelegate.LogListener getLogListener()
	{
		return logListener;
	}
	
	public void setRequestListener(MASTAdViewDelegate.RequestListener requestListener)
	{
		this.requestListener = requestListener;
	}
	
	public MASTAdViewDelegate.RequestListener getRequestListener()
	{
		return requestListener;
	}
	
	public void setRichMediaListener(MASTAdViewDelegate.RichMediaListener richMediaListener)
	{
		this.richMediaListener = richMediaListener;
	}
	
	public MASTAdViewDelegate.RichMediaListener getRichMediaListener()
	{
		return richMediaListener;
	}
	
	public void setAdNetworkURL(String adNetworkURL)
	{
		this.adNetworkURL = adNetworkURL;
	}
	
	public String getAdNetworkURL()
	{
		return adNetworkURL;
	}
	
	public Map<String, String> getAdRequestParameters()
	{
		return adRequestParameters;
	}
	
	public void setUpdateInterval(int updateInterval)
	{
		this.updateInterval = updateInterval;
	}
	
	public int getUpdateInterval()
	{
		return updateInterval;
	}
	
	public void setZone(int zone)
	{
		this.zone = zone;
	}
	
	public int getZone()
	{
		return zone;
	}
	
	public void setTest(boolean test)
	{
		this.test = test;
	}
	
	public boolean isTest()
	{
		return test;
	}
	
	public void setShowCloseButton(boolean showCloseButton)
	{
		this.showCloseButton = showCloseButton;
	}
	
	public boolean getShowCloseButton()
	{
		return showCloseButton;
	}
	
	public void setCloseButtonDelay(int closeButtonDelay)
	{
		this.closeButtonDelay = closeButtonDelay;
	}
	
	public int getCloseButtonDelay()
	{
		return closeButtonDelay;
	}
	
	public void setCloseButtonCustomDrawable(Drawable closeButtonCustomDrawable)
	{
		this.closeButtonCustomDrawable = closeButtonCustomDrawable;
	}
	
	public Drawable getCloseButtonCustomDrawable()
	{
		return closeButtonCustomDrawable;
	}
	
	public void setUseInternalBrowser(boolean useInternalBrowser)
	{
		this.useInternalBrowser = useInternalBrowser;
	}
	
	public boolean getUseInternalBrowser()
	{
		return useInternalBrowser;
	}
	
	public boolean isInternalBrowserOpen()
	{
		if ((browserDialog != null) && browserDialog.isShowing())
		{
			return true;
		}
		
		return false;
	}
	
	public boolean isLocationDetectionEnabled()
	{
		if (locationManager != null)
		{
			return true;
		}

		return false;
	}
	
	// if enabled, uses optimum criteria for battery life
	// if fine tuning is required, use enableLocationDetection
	// usesPermission access fine or coarse location
	public void setLocationDetectionEnabled(boolean locationDetectionEnabled)
	{
		if (locationDetectionEnabled == false)
		{
			if (locationManager != null)
			{
				locationManager.removeUpdates(locationListener);
				locationManager = null;
				locationListener = null;
			}
			
			return;
		}
		
		Criteria criteria = new Criteria();
		criteria.setCostAllowed(false);
		
		criteria.setPowerRequirement(Criteria.NO_REQUIREMENT);
		criteria.setBearingRequired(false);
		criteria.setSpeedRequired(false);
		criteria.setAltitudeRequired(false);
		criteria.setAccuracy(Criteria.ACCURACY_COARSE);
		
		enableLocationDetection(Defaults.LOCATION_DETECTION_MINTIME, Defaults.LOCATION_DETECTION_MINDISTANCE,
				criteria, null);
	}
	
	// see LocationManager.requestLocationUpdates
	// if  the provider is non-null then the criteria is ignored.
	// disable with setLocationDetection(false)
	public void enableLocationDetection(long minTime, float minDistance, Criteria criteria, String provider)
	{
		if ((provider == null) && (criteria == null))
			throw new IllegalArgumentException("criteria or provider required");
		
		locationManager = (LocationManager) MASTAdView.this.getContext().getSystemService(Context.LOCATION_SERVICE);
		if (locationManager != null)
		{
			try
			{
				
				if (provider == null)
				{
					List<String> providers = locationManager.getProviders(criteria, true);
					if ((providers != null) && (providers.size() > 0))
					{
						provider = providers.get(0);
					}
				}
				
				if (provider != null)
				{
					locationListener = new LocationListener();
					locationManager.requestLocationUpdates(provider, minTime, minDistance, locationListener);
				}
			}
			catch (Exception ex)
			{
				logEvent("Error requesting location updates.  Exception:" + ex, LogLevel.Error);
				
				locationManager.removeUpdates(locationListener);
				locationManager = null;
				locationListener = null;
			}
		}
	}
	
	public void setLogLevel(LogLevel logLevel)
	{
		this.logLevel = logLevel;
	}
	
	public LogLevel getLogLevel()
	{
		return logLevel;
	}
	
	public void update()
	{
		update(false);
	}
	
	public void update(boolean force)
	{
		if (zone == 0)
			throw new IllegalStateException("zone not set");
		
		if (adUpdateIntervalFuture != null)
		{
			adUpdateIntervalFuture.cancel(true);
			adUpdateIntervalFuture = null;
		}
		
		if (updateInterval > 0)
		{
			adUpdateIntervalFuture = Background.getExecutor().scheduleAtFixedRate(new Runnable()
			{
				@Override
				public void run()
				{
					internalUpdate();
				}
				
			}, 0, updateInterval, TimeUnit.SECONDS);
		}
		
	    if (force)
	    {
	    	closeInternalBrowser();

	    	if (placementType == PlacementType.Inline)
	    	{
	    		if ((mraidBridge != null) && (mraidBridgeHandler != null))
	    		{
	    			switch (mraidBridge.getState())
	    			{
	    			case Loading:
	    			case Default:
	    			case Hidden:
	    				break;

	    			case Expanded:
	    			case Resized:
	    				mraidBridgeHandler.mraidClose(mraidBridge);
	    				break;
	    			}
	    		}
	    	}
	    }
		
		internalUpdate();
	}
	
	public void reset()
	{
		deferredUpdate = false;
		
		removeContent();
		
		if (adUpdateIntervalFuture != null)
		{
			adUpdateIntervalFuture.cancel(true);
			adUpdateIntervalFuture = null;
		}
		
		if (interstitialDelayFuture != null)
		{
			interstitialDelayFuture.cancel(true);
			interstitialDelayFuture = null;
		}
		
		closeInternalBrowser();
		browserDialog = null;
		
		setLocationDetectionEnabled(false);
	}
	
	// main thread
	public void removeContent()
	{
		deferredUpdate = false;
		
		resetRichMediaAd();
		resetImageAd();
		resetTextAd();
		
		switch (placementType)
		{
		case Inline:
			removeAllViews();
			break;
			
		case Interstitial:
			interstitialDialog.removeAllViews();
		}
		
		adDescriptor = null;
	}
	
	private void internalUpdate()
	{
		deferredUpdate = false;
		
		if (isInternalBrowserOpen())
		{
			deferredUpdate = true;
			return;
		}
		
		if ((mraidBridge != null) && (mraidBridgeHandler != null))
		{
			switch (mraidBridge.getState())
			{
			case Loading:
			case Default:
			case Hidden:
				break;

			case Expanded:
			case Resized:
				deferredUpdate = true;
				return;
			}
		}
		
		if ((mraidBridge != null) && (mraidBridgeHandler != null))
    	{
    		switch (mraidBridge.getState())
    		{
    		case Loading:
    		case Default:
    		case Hidden:
    			break;

    		case Expanded:
    		case Resized:
    			mraidBridgeHandler.mraidClose(mraidBridge);
    			break;
    		}
    	}
		
		Map<String, String> args = new HashMap<String, String>();
		
		// Default size_x/y used.
		int size_x = getWidth();
		int size_y = getHeight();
		if (isInterstitial())
		{
			DisplayMetrics displayMetrics = Resources.getSystem().getDisplayMetrics();
			size_x = displayMetrics.widthPixels;
			size_y = displayMetrics.heightPixels;
		}
		
        args.put("size_x", String.valueOf(size_x));
        args.put("size_y", String.valueOf(size_y));
        
        try
        {
	        TelephonyManager tm = (TelephonyManager) getContext().getSystemService(Context.TELEPHONY_SERVICE);
			String networkOperator = tm.getNetworkOperator();      
			if ((networkOperator != null) && (networkOperator.length() > 3)) 
			{         
				String mcc = networkOperator.substring(0, 3);   
				String mnc = networkOperator.substring(3);  
				
				args.put("mcc", String.valueOf(mcc));
				args.put("mnc", String.valueOf(mnc));
	        }
        }
		catch (Exception ex)
        {
        	logEvent("Unable to obtain mcc and mnc. Exception:" + ex, LogLevel.Debug);
        }

        args.putAll(adRequestParameters);
        
        // Don't allow these to be overridden.
        args.put("ua", getUserAgent());
        args.put("version", sdkVersion);
        args.put("count", "1");
        args.put("key", "3");
        args.put("zone", String.valueOf(zone));
        
        if (this.test)
        {
        	args.put("test", "1");
        }
		
		try
		{
			if (adRequest != null)
				adRequest.cancel();
			
			adRequest = AdRequest.create(Defaults.NETWORK_TIMEOUT_SECONDS, adNetworkURL,
					userAgent, args, adRequestHandler);
			
			String requestUrl = adRequest.getRequestUrl();
			logEvent("Ad request:" + requestUrl, LogLevel.Debug);
		}
		catch (UnsupportedEncodingException e)
		{
			logEvent("Exception encountered while generating ad request URL:" + e, LogLevel.Error);
			
			if (requestListener != null)
			{
				requestListener.onFailedToReceiveAd(this, e);
			}
		}
	}
	
	public void showInterstitial()
	{
		showInterstitialWithDuration(0);
	}
	
	public void showInterstitialWithDuration(int durationSeconds)
	{
		if (isInterstitial() == false)
			throw new IllegalStateException("showInterstitial requires interstitial instance");
		
		if (interstitialDelayFuture != null)
		{
			interstitialDelayFuture.cancel(true);
			interstitialDelayFuture = null;
		}
		
		interstitialDialog.show();
		
		prepareCloseButton();
		performAdTracking();
		
		if (durationSeconds > 0)
		{
			interstitialDelayFuture = Background.getExecutor().schedule(new Runnable()
			{
				@Override
				public void run()
				{
					closeInterstitial();
				}
				
			}, durationSeconds, TimeUnit.SECONDS);
		}
	}
	
	// main/background thread
	public void closeInterstitial()
	{
		if (interstitialDelayFuture != null)
		{
			interstitialDelayFuture.cancel(true);
			interstitialDelayFuture = null;
		}
		
		if (interstitialDialog != null)
		{
			interstitialDialog.dismiss();
		}
	}

	public ImageView getImageView()
	{
		if (imageView == null)
		{
			imageView = new ImageView(getContext());
		}
		
		return imageView;
	}
	
	public TextView getTextView()
	{
		if (textView == null)
		{
			textView = new TextView(getContext());
			textView.setGravity(Gravity.CENTER);
		}
		
		return textView;
	}
	
	public android.webkit.WebView getWebView()
	{
		if (webView == null)
		{
			webView = new WebView(getContext());
			webView.setHandler(webViewHandler);
		}
		
		return webView;
	}
	
	private void addContentView(View view, LayoutParams layoutParams)
	{
		switch (placementType)
		{
		case Inline:
			if (view.getParent() != this)
			{
				if (view.getParent() != null)
				{
					ViewGroup viewGroup = (ViewGroup) view.getParent();
					viewGroup.removeView(view);
				}
				
				addView(view, layoutParams);
			}
			break;
			
		case Interstitial:
			interstitialDialog.addView(view);
			break;
		}
	}
	
	// background/main thread
	private void renderAdDescriptor(final AdDescriptor adDescriptor)
	{
		if (adDescriptor == null)
			throw new IllegalArgumentException("adDescriptor null");
		
		invokeTracking = true;
		
		String adType = adDescriptor.getType();
		if (adType.startsWith("image"))
		{
			String img = adDescriptor.getImage();
			fetchImage(adDescriptor, img);
			return;
		}

        if (adType.startsWith("text"))
        {
        	final String txt = adDescriptor.getText();
            
        	runOnUiThread(new Runnable()
        	{
            	public void run()
            	{
        			renderText(adDescriptor, txt);
            	}
            });
        	
            return;
        }
        
        String content = adDescriptor.getContent();

        if (adType.startsWith("thirdparty"))
        {
        	String url = adDescriptor.getURL();
            if (TextUtils.isEmpty(url) == false)
            {
            	String img = adDescriptor.getImage();
                if (TextUtils.isEmpty(img) == false)
                {
                	fetchImage(adDescriptor, img);
                    return;
                }

                final String txt = adDescriptor.getText();
                if (TextUtils.isEmpty(txt) == false)
                {
                	runOnUiThread(new Runnable()
                	{
                    	public void run()
                    	{
                			renderText(adDescriptor, txt);
                    	}
                    });
                    return;
                }
            }
            else if (TextUtils.isEmpty(content) == false)
            {
                if (content.contains("client_side_external_campaign") == true)
                {
                	// TODO: Need third party parser and descriptor.
                	// TODO: Invoke delegate on received third party request.
                    return;
                }
            }
        }

        if (TextUtils.isEmpty(content))
        {
        	logEvent("Ad descriptor missing ad content", LogLevel.Error);
        	
        	if (requestListener != null)
        	{
        		requestListener.onFailedToReceiveAd(this, null);
        	}

            return;
        }
        
        runOnUiThread(new Runnable()
        {
        	public void run()
        	{
        		renderRichMedia(adDescriptor);
        	}
        });
	}
	
	// main/background thread
	private void performAdTracking()
	{
		if (invokeTracking && (adDescriptor != null))
		{
			invokeTracking = false;
			
			String url = adDescriptor.getTrack();
			if (TextUtils.isEmpty(url) == false)
			{
				AdTracking.invokeTrackingUrl(Defaults.NETWORK_TIMEOUT_SECONDS, url, userAgent);
			}
		}
	}
	
	// main/background thread
	private void fetchImage(final AdDescriptor adDescriptor, final String url)
	{
		ImageRequest.create(Defaults.NETWORK_TIMEOUT_SECONDS, url, getUserAgent(), true, new ImageRequest.Handler()
		{
			@Override
			public void imageFailed(ImageRequest request, Exception ex)
			{
				logEvent("Image download failure.  Exception:" + ex, LogLevel.Error);
				
				if (requestListener != null)
				{
					requestListener.onFailedToReceiveAd(MASTAdView.this, ex);
				}
			}

			@Override
			public void imageReceived(ImageRequest request, Object imageObject)
			{
				final Object finalImaegObject = imageObject;
				
		        runOnUiThread(new Runnable()
		        {
		        	public void run()
		        	{
						renderImage(adDescriptor, finalImaegObject);
		        	}
		        });
			}
		});
	}
	
	// main thread
	private void renderImage(AdDescriptor adDescriptor, Object imageObject)
	{
		resetTextAd();
		resetRichMediaAd();
		
		getImageView();
		
		LayoutParams layoutParams = 
				new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
		
		addContentView(imageView, layoutParams);
		
		if (imageObject instanceof Bitmap)
		{
			imageView.setImageBitmap((Bitmap) imageObject);
		}
		else if (imageObject instanceof GifDecoder)
		{
			imageView.setImageGifDecoder((GifDecoder) imageObject);
		}

		this.adDescriptor = adDescriptor;

		prepareCloseButton();
		performAdTracking();
				
		if (requestListener != null)
		{
			requestListener.onReceivedAd(MASTAdView.this);
		}
	}
	
	private void resetImageAd()
	{
		if (imageView != null)
		{
			imageView.setImageBitmap(null);
		}

		adDescriptor = null;
	}
	
	// main thread
	private void renderText(AdDescriptor adDescriptor, String text)
	{
		resetImageAd();
		resetRichMediaAd();
		
		getTextView();
		
		LayoutParams layoutParams = 
				new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
		
		addContentView(textView, layoutParams);
		
		textView.setText(text);
		
		this.adDescriptor = adDescriptor;
		
		prepareCloseButton();
		performAdTracking();
		
		if (requestListener != null)
		{
			requestListener.onReceivedAd(MASTAdView.this);
		}
	}
	
	private void resetTextAd()
	{
		if (textView != null)
		{
			textView.setText("");
		}
		
		adDescriptor = null;
	}
	
	// main thread
	private void renderRichMedia(AdDescriptor adDescriptor)
	{
		invokeTracking = false;
		
		resetImageAd();
		resetTextAd();
		
		getWebView().stopLoading();

		LayoutParams layoutParams = 
				new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
		
		addContentView(webView, layoutParams);
		
		mraidBridgeInit = false;
		mraidBridge = new Bridge(webView, mraidBridgeHandler);
		
		String fragment = adDescriptor.getContent();
		webView.loadFragment(fragment, mraidBridge);

		this.adDescriptor = adDescriptor;
		
		if (requestListener != null)
		{
			requestListener.onReceivedAd(MASTAdView.this);
		}
	}
	
	private void resetRichMediaAd()
	{
		if (mraidBridge != null)
		{
			mraidBridgeHandler.mraidClose(mraidBridge);
			
			if (mraidExpandDialog != null)
			{
				mraidExpandDialog.dismiss();
				mraidExpandDialog = null;
			}
			
			if (mraidResizeLayout != null)
			{
				ViewGroup parent = (ViewGroup) mraidResizeLayout.getParent();
				if (parent != null)
				{
					parent.removeView(mraidResizeLayout);
				}

				mraidResizeLayout = null;
				mraidResizeCloseArea = null;
			}
			
			mraidBridge = null;
		}
		
		if (webView != null)
		{
			webView.clearView();
			webView.clearHistory();
		}
		
		adDescriptor = null;
	}
	
	// main thread
	private void renderTwoPartExpand(String url)
	{
		mraidTwoPartExpand = true;
		
		mraidTwoPartWebView = new WebView(getContext());
		mraidTwoPartWebView.setHandler(webViewHandler);
		mraidTwoPartBridgeInit = false;
		mraidTwoPartBridge = new Bridge(mraidTwoPartWebView, mraidBridgeHandler);

		mraidTwoPartWebView.loadUrl(url);
		
		mraidExpandDialog = new ExpandDialog(getContext());
		mraidExpandDialog.addView(mraidTwoPartWebView);
		mraidExpandDialog.show();
	}
	
	@Override
	protected void onAttachedToWindow()
	{
		super.onAttachedToWindow();
		
		performAdTracking();
	}
	
	@Override
	protected void onDetachedFromWindow()
	{	
		super.onDetachedFromWindow();
		
		// TODO: NPE when resized and activity is destoryed.
		
		if (mraidBridge != null)
		{
			switch (mraidBridge.getState())
			{
			case Loading:
			case Hidden:
			case Default:
				break;
				
			case Resized:
			case Expanded:
				mraidBridgeHandler.mraidClose(mraidBridge);
				break;
			}
		}
	}

	@Override
	protected void onLayout(boolean changed, int l, int t, int r, int b)
	{
		if (webView != null)
		{
			if (webView.getParent() == this)
				webView.layout(0, 0, getWidth(), getHeight());
			
			if (mraidBridge != null)
			{
				if ((changed == false) && webView.hasFocus())
					return;
				
				updateMRAIDLayoutForState(mraidBridge, mraidBridge.getState());
			}
		}
		
		if (imageView != null)
		{
			imageView.layout(0, 0, getWidth(), getHeight());
		}
		
		if (textView != null)
		{
			textView.layout(0, 0, getWidth(), getHeight());
		}
		
		updateOnLayout();
	}
	
	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec)
	{
		super.onMeasure(widthMeasureSpec, heightMeasureSpec);
		super.measureChildren(widthMeasureSpec, heightMeasureSpec);
	}
	
	@Override
	protected Parcelable onSaveInstanceState()
	{
		Parcelable parcelable = super.onSaveInstanceState();
		
		if (adDescriptor == null)
			return parcelable;
		
		return parcelable;
	}
	
	@Override
	protected void onRestoreInstanceState(Parcelable parcelable)
	{
		super.onRestoreInstanceState(parcelable);
	}
	
	@Override
	public boolean onTouchEvent(MotionEvent event)
	{	
		switch (event.getAction())
		{
		case MotionEvent.ACTION_DOWN:
			if (((imageView != null) && (imageView.getParent() == this)) ||
				((textView != null) && (textView.getParent() == this)))
			{
				if ((adDescriptor != null) && (TextUtils.isEmpty(adDescriptor.getURL()) == false))
				{
					openUrl(adDescriptor.getURL(), false);
					return true;
				}
			}
			break;
		}
		
		return false;
	}
	
	// background/main thread
	private void openUrl(final String url, final boolean bypassInternalBrowser)
	{
		if (activityListener != null)
		{
			if (activityListener.onOpenUrl(this, url) == true)
			{
				return;
			}
		}
		
		runOnUiThread(new Runnable()
		{
			public void run()
			{
				if ((bypassInternalBrowser == false) && useInternalBrowser)
				{
					openInternalBrowser(url);
					return;
				}

				if (activityListener != null)
				{
					activityListener.onLeavingApplication(MASTAdView.this);
				}
				
				// TODO: Returning application delegate?
				// TODO: Suspend updating (if inline update with visibility set to invisible?)?
				Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
				getContext().startActivity(intent);
			}
		});
	}
	
	// main thread
	private void openInternalBrowser(String url)
	{
		if (browserDialog == null)
		{
			browserDialog = new BrowserDialog(getContext(), url, new BrowserDialog.Handler()
			{
				@Override
				public void browserDialogDismissed(BrowserDialog browserDialog)
				{
					if (internalBrowserListener != null)
					{
						internalBrowserListener.onInternalBrowserDismissed(MASTAdView.this);
					}
				}

				@Override
				public void browserDialogOpenUrl(BrowserDialog browserDialog, String url)
				{
					openUrl(url, true);
					browserDialog.dismiss();
				}
			});
		}
		else
		{
			browserDialog.loadUrl(url);
		}
		
		if (browserDialog.isShowing() == false)
		{
			browserDialog.show();
		}
		
		if (internalBrowserListener != null)
		{
			internalBrowserListener.onInternalBrowserPresented(this);
		}
	}
	
	// main thread
	private void closeInternalBrowser()
	{
		if (browserDialog != null)
		{
			if (browserDialog.isShowing())
			{
				browserDialog.dismiss();
			}
		}
	}
	
	private void initMRAIDBridge(Bridge bridge)
	{
		if (bridge == null)
			return;
		
		synchronized (bridge)
		{
			if ((bridge == mraidBridge) && (mraidBridgeInit == false))
			{
				return;
			}
			else if ((bridge == mraidTwoPartBridge) && (mraidTwoPartBridgeInit == false))
			{
				return;
			}
			
			if (bridge.webView.isLoaded() == false)
				return;
			
			if (bridge.getState() != Consts.State.Loading)
				return;
			
			// Initialize the bridge.
			bridge.setPlacementType(placementType);
			
			setMRAIDSupportedFeatures(bridge);
			
			if (bridge == mraidBridge)
			{
				updateMRAIDLayoutForState(bridge, State.Default);
				bridge.setState(State.Default);
			}
			else
			{
				updateMRAIDLayoutForState(bridge, State.Expanded);
				bridge.setState(State.Expanded);
			}
			
			bridge.sendReady();
		}
	}
	
	private void setMRAIDSupportedFeatures(Bridge bridge)
	{
		if (bridge == null)
			return;
		
		Boolean smsSupported = null;
		Boolean phoneSupported = null;
		Boolean calendarSupported = null;
		Boolean pictureSupported = null;
		
		if (featureSupportHandler != null)
		{
			smsSupported = featureSupportHandler.shouldSupportSMS(this); 
			phoneSupported = featureSupportHandler.shouldSupportPhone(this);
			calendarSupported = featureSupportHandler.shouldSupportCalendar(this);
			pictureSupported = featureSupportHandler.shouldSupportStorePicture(this);
		}

		if (smsSupported == null)
		{
			smsSupported = getContext().checkCallingOrSelfPermission(android.Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED;
		}
		if (phoneSupported == null)
		{
			phoneSupported = getContext().checkCallingOrSelfPermission(android.Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED;
		}
		if (calendarSupported == null)
		{
			calendarSupported = ((getContext().checkCallingOrSelfPermission(android.Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED) &&
					 (getContext().checkCallingOrSelfPermission(android.Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED));
		}
		if (pictureSupported == null)
		{
			pictureSupported = getContext().checkCallingOrSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
		}
		
		bridge.setSupportedFeature(Feature.SMS, smsSupported);
		bridge.setSupportedFeature(Feature.Tel, phoneSupported);
		bridge.setSupportedFeature(Feature.Calendar, calendarSupported);
		bridge.setSupportedFeature(Feature.StorePicture, pictureSupported);
		bridge.setSupportedFeature(Feature.InlineVideo, false);
	}
	
	private void updateMRAIDLayoutForState(Bridge bridge, State state)
	{
		WebView webView = this.webView;
		if (bridge == mraidTwoPartBridge)
			webView = mraidTwoPartWebView;
		
		boolean viewable = webView.isShown();
		
		DisplayMetrics displayMetrics = Resources.getSystem().getDisplayMetrics();
		View rootView = getRootView();
			
		float defaultWidthPx = getWidth();
		float defaultHeightPx = getHeight();
		int defaultWidthDp = pxToDp(defaultWidthPx);
		int defaultHeightDp = pxToDp(defaultHeightPx);
		
		float currentWidthPx = webView.getWidth();
		float currentHeightPx = webView.getHeight();
		int currentWidthDp = pxToDp(currentWidthPx);
		int currentHeightDp = pxToDp(currentHeightPx);
		
        int[] containerScreenLocation = new int[2];
        getLocationOnScreen(containerScreenLocation);
        int containerScreenX = pxToDp(containerScreenLocation[0]);
		int containerScreenY = pxToDp(containerScreenLocation[1]);
		
        int[] webViewScreenLocation = new int[2];
		if ((state == State.Resized) && (mraidResizeLayout != null))
		{
			RelativeLayout.LayoutParams webViewLayoutParams = 
					(RelativeLayout.LayoutParams) webView.getLayoutParams();
			webViewScreenLocation[0] = webViewLayoutParams.leftMargin;
			webViewScreenLocation[1] = webViewLayoutParams.topMargin;
		}
		else
		{
			webView.getLocationOnScreen(webViewScreenLocation);
		}
		int webViewScreenX = pxToDp(webViewScreenLocation[0]);
		int webViewScreenY = pxToDp(webViewScreenLocation[1]);

		int screenWidthDp = pxToDp(displayMetrics.widthPixels);
		int screenHeightDp = pxToDp(displayMetrics.heightPixels);
		int maxWidthDp = pxToDp(rootView.getWidth());
		int maxHeightDp = pxToDp(rootView.getHeight());

		// Android fails at notifying on post-presentation so we'll use
		// the crystal ball and foresee what it should do.
		switch (state)
		{
		case Loading:
			break;
			
		case Default:
			webViewScreenX = containerScreenX;
			webViewScreenY = containerScreenY;
			currentWidthDp = defaultWidthDp;
			currentHeightDp = defaultHeightDp;
			break;

		case Hidden:
		case Resized:
			break;
			
		case Expanded:
			webViewScreenX = 0;
			webViewScreenY = 0;
			currentWidthDp = screenWidthDp;
			currentHeightDp = screenHeightDp;
		}

		if (placementType == PlacementType.Interstitial)
		{
			containerScreenX = 0;
			containerScreenY = 0;
			maxWidthDp = screenWidthDp;
			maxHeightDp = screenHeightDp;
			defaultWidthDp = screenWidthDp;
			defaultHeightDp = screenHeightDp;
			currentWidthDp = screenWidthDp;
			currentHeightDp = screenHeightDp;
		}
	    
		bridge.setScreenSize(screenWidthDp, screenHeightDp);
		bridge.setMaxSize(maxWidthDp, maxHeightDp);
		bridge.setDefaultPosition(containerScreenX, containerScreenY, defaultWidthDp, defaultHeightDp);
		bridge.setCurrentPosition(webViewScreenX, webViewScreenY, currentWidthDp, currentHeightDp);
		bridge.setViewable(viewable);
	}
	
	// main thread
	private void setMRAIDOrientation()
	{
		Activity activity = getActivity();
		if (activity == null)
			return;
		
		if (mraidOriginalOrientation == OrientationReset)
		{
			mraidOriginalOrientation = activity.getRequestedOrientation();
		}
		
		OrientationProperties orientationProperties = mraidBridge.getOrientationProperties();
		
		ForceOrientation forceOrientation = orientationProperties.getForceOrientation();
		switch (forceOrientation)
		{
			case Portrait:
				activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
				break;
			case Landscape:
				activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
				break;
			case None:
				break;
		}
		
		if (orientationProperties.getAllowOrientationChange() == true)
		{
			activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
		}
		else
		{
			if (forceOrientation == ForceOrientation.None)
			{
				int currentOrientation = activity.getResources().getConfiguration().orientation;
				
				switch (currentOrientation)
				{
				case Configuration.ORIENTATION_PORTRAIT:
					activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
					break;
				case Configuration.ORIENTATION_LANDSCAPE:
					activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
					break;
				default:
					activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_NOSENSOR);
					break;
				}
			}
		}
	}
	
	// main thread
	private void resetMRAIDOrientation()
	{
		Activity activity = getActivity();
		if (activity == null)
			return;
		
		if (mraidOriginalOrientation != OrientationReset)
		{
			activity.setRequestedOrientation(mraidOriginalOrientation);
		}
	}
	
	// main thread
	private void prepareCloseButton()
	{
		if (closeButtonFuture != null)
		{
			closeButtonFuture.cancel(true);
			closeButtonFuture = null;
		}
		
	    if (mraidBridge != null)
	    {
	        switch (mraidBridge.getState())
	        {
	            case Expanded:
	                // When expanded use the built in button or the custom one, else nothing else.
	                if (mraidBridge.getExpandProperties().useCustomClose() == false)
	                {
	                    showCloseButton();
	                }
	                return;
	                
	            case Resized:
	                // The ad creative MUST supply it's own close button.
	                return;
	                
	            default:
	                break;
	        }
	    }
	    
	    if (closeButtonDelay < 0)
	        return;
	    
	    if (closeButtonDelay == 0)
	    {
	        showCloseButton();
	        return;
	    }
	    
	    closeButtonFuture = Background.getExecutor().schedule(new Runnable()
		{
			@Override
			public void run()
			{
				showCloseButton();
			}
			
		}, closeButtonDelay, TimeUnit.SECONDS);
	}
	
	// main thread
	@SuppressWarnings("deprecation")
	private void showCloseButton()
	{
		Drawable closeButtonDrawable = closeButtonCustomDrawable;
		
		if (closeButtonDrawable == null)
		{
			try
			{
				InputStream is = WebView.class.getResourceAsStream("/close_button.png");
				closeButtonDrawable = new BitmapDrawable(getResources(), is);
				((BitmapDrawable) closeButtonDrawable).setGravity(Gravity.CENTER);
			}
			catch (Exception ex)
			{
				logEvent("Error loading built in close button.  Exception:" + ex, LogLevel.Error);
			}
		}
		
		if (closeButtonDrawable == null)
			return;
		
	    if (mraidBridge != null)
	    {
	        switch (mraidBridge.getState())
	        {
	            case Loading:
	            case Default:
	            case Hidden:
	                // Like text or image ads just put the close button at the top of the stack
	                // on the ad view and not on the webview.
	                break;
	                
	            case Expanded:
	                mraidExpandDialog.setCloseImage(closeButtonDrawable);
	                return;
	                
	            case Resized:
	            	// Supporting API8 and higher.  Avoiding reflection for now.
	                mraidResizeCloseArea.setBackgroundDrawable(closeButtonDrawable);
	                return;
	        }
	    }
	    
	    switch (placementType)
	    {
	        case Inline:
	        {
	        	// TODO: Support inline close button?
	            break;
	        }

	        case Interstitial:
	        {
	        	interstitialDialog.setCloseImage(closeButtonDrawable);
	            break;
	        }
	    }
	}
	
	private class WebViewHandler implements WebView.Handler
	{
		@Override
		public void webViewPageStarted(WebView webView)
		{
			
		}

		@Override
		public void webViewPageFinished(WebView webView)
		{
			if (mraidBridge.webView == webView)
			{
				initMRAIDBridge(mraidBridge);
			}
			else if ((mraidTwoPartBridge != null) && (mraidTwoPartBridge.webView == webView))
			{
				initMRAIDBridge(mraidTwoPartBridge);
			}
		}

		@Override
		public void webViewReceivedError(WebView webView, int errorCode, String description, String failingUrl)
		{
			resetRichMediaAd();
			
			logEvent("Error loading rich media ad content.  Error code:" + String.valueOf(errorCode) + 
					" Description:" + description, LogLevel.Error);

			if (requestListener != null)
			{
				requestListener.onFailedToReceiveAd(MASTAdView.this, null);
			}
			
			removeContent();
		}

		@Override
		public boolean webViewshouldOverrideUrlLoading(WebView view, String url)
		{
			openUrl(url, false);
			return true;
		}
	}
	
	private class MRAIDHandler implements Bridge.Handler
	{
		@Override
		public void mraidInit(final Bridge bridge)
		{
			if ((bridge != mraidBridge) && (bridge != mraidTwoPartBridge))
				return;
			
			if (bridge == mraidBridge)
			{
				mraidBridgeInit = true;
			}
			else if (bridge == mraidTwoPartBridge)
			{
				mraidTwoPartBridgeInit = true;
			}

			initMRAIDBridge(bridge);
		}
		
		@Override
		public void mraidClose(final Bridge bridge)
		{
			if ((bridge != mraidBridge) && (bridge != mraidTwoPartBridge))
				return;
			
			if (placementType == PlacementType.Interstitial)
			{
				if (activityListener != null)
				{
					activityListener.onCloseButtonClick(MASTAdView.this);
				}

				return;
			}
			
			switch (bridge.getState())
			{
			case Loading:
			case Hidden:
				break;
				
			case Default:
				// MRAID specification is weak in this case so ignoring.
				break;

			case Resized:
				runOnUiThread(new Runnable()
				{
					public void run()
					{
						if (mraidResizeLayout == null)
							return;
						
						ViewGroup parent = (ViewGroup) webView.getParent();
						if (parent != null)
						{
							parent.removeView(webView);
						}
						parent = (ViewGroup) mraidResizeLayout.getParent();
						if (parent != null)
						{
							parent.removeView(mraidResizeLayout);
						}
						mraidResizeLayout = null;
						mraidResizeCloseArea = null;
						
						LayoutParams layoutParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
						addView(webView, layoutParams);

						updateMRAIDLayoutForState(bridge, State.Default);
						bridge.setState(State.Default);
						
						if (richMediaListener != null)
						{
							richMediaListener.onCollapsed(MASTAdView.this);
						}
						
						if (bridge == mraidBridge)
						{
							if (deferredUpdate)
								update();
						}
					}
				});
				break;
				
			case Expanded:
				if (mraidExpandDialog == null)
					return;
				
				mraidExpandDialog.dismiss();
				runOnUiThread(new Runnable()
				{
					public void run()
					{
						// TODO: Race condition... if the creative calls close 
						// this will invoke dismiss but the dismiss handler will
						// also turn around and call close.  One of them is triggering
						// an NPE.
						if (mraidTwoPartExpand == false)
						{
							mraidExpandDialog.removeView(webView);
							addView(webView);
						}
						else
						{
							mraidExpandDialog.removeView(mraidTwoPartWebView);
							
							mraidTwoPartWebView = null;
							mraidTwoPartBridge = null;
							mraidTwoPartExpand = false;
						}
						
						mraidExpandDialog = null;
						
						// For normal or two part expand the original bridge
						// gets reset back to the default state.
						updateMRAIDLayoutForState(mraidBridge, State.Default);
						mraidBridge.setState(State.Default);
						
						resetMRAIDOrientation();
						
						if (richMediaListener != null)
						{
							richMediaListener.onCollapsed(MASTAdView.this);
						}
						
						if (bridge == mraidBridge)
						{
							if (deferredUpdate)
								update();
						}
					}
				});
				break;
			}
		}

		@Override
		public void mraidOpen(final Bridge bridge, String url)
		{
			if ((bridge != mraidBridge) && (bridge != mraidTwoPartBridge))
				return;

			openUrl(url, false);
		}

		@Override
		public void mraidUpdateCurrentPosition(final Bridge bridge)
		{
			if ((bridge != mraidBridge) && (bridge != mraidTwoPartBridge))
				return;
			
			updateMRAIDLayoutForState(bridge, bridge.getState());
		}

		@Override
		public void mraidUpdatedExpandProperties(final Bridge bridge)
		{
			if ((bridge != mraidBridge) && (bridge != mraidTwoPartBridge))
				return;
			
			prepareCloseButton();
		}

		@Override
		public void mraidExpand(final Bridge bridge, final String url)
		{
			if ((bridge != mraidBridge) && (bridge != mraidTwoPartBridge))
				return;
			
			if (placementType == PlacementType.Interstitial)
			{
				bridge.sendErrorMessage("Can not expand with placementType interstitial.", Consts.CommandExpand);
				return;
			}
			
			boolean hasUrl = false;
			if (TextUtils.isEmpty(url) == false)
			{
				hasUrl = true;
			}
			
			switch (bridge.getState())
			{
			case Loading:
				if (mraidTwoPartExpand && (hasUrl == false))
				{
					// This is the SDK setting the expand state when initializing 
					//  the two part expand operation.
					break;
				}

				bridge.sendErrorMessage("Can not expand while state is loading.", Consts.CommandExpand);
				return;
				
			case Hidden:
				// Expand from this state is a no-op.
				return;

			case Expanded:
				bridge.sendErrorMessage("Can not expand while state is expanded.", Consts.CommandExpand);
				return;

			case Default:
			case Resized:
				// Expand permitted.
				break;
			}
			
			if (hasUrl == false)
			{
				// Normal expand.
				runOnUiThread(new Runnable()
				{
					public void run()
					{
						removeView(webView);
						
						mraidExpandDialog = new ExpandDialog(getContext());
						mraidExpandDialog.addView(webView);
						mraidExpandDialog.show();
					}
				});
			}
			else
			{
				// Two part expand.
				runOnUiThread(new Runnable()
				{
					@Override
					public void run() 
					{
						renderTwoPartExpand(url);
					}
				});
			}
		}

		@Override
		public void mraidUpdatedOrientationProperties(final Bridge bridge)
		{
			if ((bridge != mraidBridge) && (bridge != mraidTwoPartBridge))
				return;
			
			// TODO: This should only be allowed when in the expanded state.
			// Also, on the very first change of orientation the current state
			// needs to be captured and restored when the ad is collapsed.

			runOnUiThread(new Runnable()
			{
				public void run()
				{
					setMRAIDOrientation();
				}
			});
		}

		@Override
		public void mraidUpdatedResizeProperties(final Bridge bridge)
		{
			// Nothing to act on here (bridge has properties updated).
		}

		@Override
		public void mraidResize(final Bridge bridge)
		{
			if ((bridge != mraidBridge) && (bridge != mraidTwoPartBridge))
				return;
			
			if (placementType == PlacementType.Interstitial)
			{
				bridge.sendErrorMessage("Can not resize with placementType interstitial.", Consts.CommandResize);
				return;
			}
			
			switch (bridge.getState())
			{
			case Loading:
			case Hidden:
			case Expanded:
				bridge.sendErrorMessage("Can not resize loading, hidden or expanded.", Consts.CommandResize);
				return;
				
			case Default:
			case Resized:
				// Resize permitted.
				break;
			}

			DisplayMetrics displayMetrics = Resources.getSystem().getDisplayMetrics();
			int screenWidth = pxToDp(displayMetrics.widthPixels);
			int screenHeight = pxToDp(displayMetrics.heightPixels);
			
			int[] currentScreenLocation = new int[2];
			getLocationOnScreen(currentScreenLocation);
			int currentX = pxToDp(currentScreenLocation[0]);
			int currentY = pxToDp(currentScreenLocation[1]);

			ResizeProperties resizeProperties = bridge.getResizeProperties();
			boolean allowOffscreen = resizeProperties.getAllowOffscreen();
			int x = currentX + resizeProperties.getOffsetX();
			int y = currentY + resizeProperties.getOffsetY();
			int width = resizeProperties.getWidth();
			int height = resizeProperties.getHeight();
			Consts.CustomClosePosition customClosePosition = resizeProperties.getCustomClosePosition();

			if ((width >= screenWidth) && (height >= screenHeight))
			{
				bridge.sendErrorMessage("Size must be smaller than the max size.", Consts.CommandResize);
				return;
			}
			else if ((width < CloseAreaSizeDp) || (height < CloseAreaSizeDp))
			{
				bridge.sendErrorMessage("Size must be at least the minimum close area size.", Consts.CommandResize);
				return;
			}
			
			int minX = 0;
			int minY = statusBarHeightDp();

			if (allowOffscreen == false)
			{
				int desiredScreenX = x;
				int desiredScreenY = y;
				int resultingScreenX = desiredScreenX;
				int resultingScreenY = desiredScreenY;

				if (width > screenWidth)
					width = screenWidth;

				if (height > screenHeight)
					height = screenHeight;

				if (desiredScreenX < minX)
				{
					resultingScreenX = minX;
				}
				else if ((desiredScreenX + width) > screenWidth)
				{
					double diff = desiredScreenX + width - screenWidth;
					resultingScreenX -= diff;
				}

				if (desiredScreenY < minY)
				{
					resultingScreenY = minY;
				}
				else if ((desiredScreenY + height) > screenHeight)
				{
					double diff = desiredScreenY + height - screenHeight;
					resultingScreenY -= diff;
				}

				double adjustedX = desiredScreenX - resultingScreenX;
				double adjustedY = desiredScreenY - resultingScreenY;
				x -= adjustedX;
				y -= adjustedY;
			}

			// Determine where the close control area will be.  This MUST be on screen.
			// By default it is in the top right but the ad can specify where it should be.
			// The ad MUST provide the graphic for it or some other means to close the resize.
			// These coordinates are relative to the container (the resized view).
			int closeControlX = width - CloseAreaSizeDp;
			int closeControlY = 0;

			switch (customClosePosition)
			{
			case TopRight:
				// Already configured above.
				break;

			case TopCenter:
				closeControlX = width/2 - CloseAreaSizeDp/2;
				closeControlY = 0;
				break;

			case TopLeft:
				closeControlX = 0;
				closeControlY = 0;
				break;

			case BottomLeft:
				closeControlX = 0;
				closeControlY = height - CloseAreaSizeDp;
				break;

			case BottomCenter:
				closeControlX = width/2 - CloseAreaSizeDp/2;
				closeControlY = height - CloseAreaSizeDp;
				break;

			case BottomRight:
				closeControlX = width - CloseAreaSizeDp;
				closeControlY = height - CloseAreaSizeDp;
				break;

			case Center:
				closeControlX = width / 2 - CloseAreaSizeDp / 2;
				closeControlY = height/2 - CloseAreaSizeDp/2;
				break;
			}

			int resultingCloseControlX = x + closeControlX;
			int resultingCloseControlY = y + closeControlY;
			int resultingCloseControlR = resultingCloseControlX + CloseAreaSizeDp;
			int resultingCloseControlB = resultingCloseControlY + CloseAreaSizeDp;
			
			if ((resultingCloseControlX < minX) || (resultingCloseControlY < minY) ||
				(resultingCloseControlR > screenWidth) || (resultingCloseControlB > screenHeight))
			{
				bridge.sendErrorMessage("Resize close control must remain on screen.", Consts.CommandResize);
				return;
			}
			
			// Convert to pixel values.
			final int xPx = dpToPx(x);
			final int yPx = dpToPx(y);
			final int widthPx = dpToPx(width);
			final int heightPx = dpToPx(height);
			final int closeXPx = dpToPx(resultingCloseControlX);
			final int closeYPx = dpToPx(resultingCloseControlY);
			
			runOnUiThread(new Runnable()
			{
				public void run()
				{
					Activity activity = getActivity();
					ViewGroup windowDecorView = (ViewGroup) activity.getWindow().getDecorView();
					
					RelativeLayout.LayoutParams webViewLayoutParams = 
							new RelativeLayout.LayoutParams(widthPx, heightPx);
					webViewLayoutParams.setMargins(xPx, yPx, Integer.MIN_VALUE, Integer.MIN_VALUE);
					
					RelativeLayout.LayoutParams closeControlLayoutParams = 
							new RelativeLayout.LayoutParams(dpToPx(CloseAreaSizeDp), dpToPx(CloseAreaSizeDp));
					closeControlLayoutParams.setMargins(closeXPx, closeYPx, Integer.MIN_VALUE, Integer.MIN_VALUE);

					if (mraidResizeLayout == null)
					{
						ViewGroup webViewParent = (ViewGroup) webView.getParent();
						if (webViewParent != null)
						{
							webViewParent.removeView(webView);
						}
						
						mraidResizeCloseArea = new View(getContext());
						mraidResizeCloseArea.setBackgroundColor(0x00000000);
						mraidResizeCloseArea.setOnClickListener(new OnClickListener()
						{
							@Override
							public void onClick(View v)
							{
								if (v != mraidResizeCloseArea)
									return;
								
								mraidBridgeHandler.mraidClose(bridge);
							}
						});
						
						mraidResizeLayout = new RelativeLayout(getContext());
						mraidResizeLayout.addView(webView, webViewLayoutParams);
						mraidResizeLayout.addView(mraidResizeCloseArea, closeControlLayoutParams);

						LayoutParams resizeLayoutParams = 
								new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
						windowDecorView.addView(mraidResizeLayout, 0, resizeLayoutParams);
						windowDecorView.bringChildToFront(mraidResizeLayout);
					}
					else
					{
						mraidResizeLayout.updateViewLayout(webView, webViewLayoutParams);
						mraidResizeLayout.updateViewLayout(mraidResizeCloseArea, closeControlLayoutParams);
					}
					
		            updateMRAIDLayoutForState(bridge, State.Resized);
		            bridge.setState(State.Resized);
		            
		            // TODO:PrepareCloseButton();
		            
		            if (richMediaListener != null)
		            {
		            	richMediaListener.onResized(MASTAdView.this, new Rect(xPx, yPx, widthPx, heightPx));
		            }
				}
			});
		}

		@Override
		public void mraidPlayVideo(final Bridge bridge, String url)
		{
			if ((bridge != mraidBridge) && (bridge != mraidTwoPartBridge))
				return;
			
			if (richMediaListener != null)
			{
				if (richMediaListener.onPlayVideo(MASTAdView.this, url))
				{
					return;
				}
			}
			
			openUrl(url, true);
		}

		@Override
		public void mraidCreateCalendarEvent(final Bridge bridge, String calendarEvent)
		{
			if ((bridge != mraidBridge) && (bridge != mraidTwoPartBridge))
				return;
			
			if (featureSupportHandler != null)
			{
				if (featureSupportHandler.shouldAddCalendarEntry(MASTAdView.this, calendarEvent) == false)
				{
					bridge.sendErrorMessage("Access denied.", Consts.CommandCreateCalendarEvent);
					return;
				}
			}
			
			try 
			{
				DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mmZ");
				
				JSONObject jsonEvent = new JSONObject(calendarEvent);
				
				final Intent intent = new Intent(Intent.ACTION_EDIT); 
				intent.setType("vnd.android.cursor.item/event"); 

				if (jsonEvent.has("start"))
				{
					String value = jsonEvent.getString("start");
					long time =  dateFormat.parse(value).getTime();
					intent.putExtra("beginTime", time);
				}
				
				if (jsonEvent.has("end"))
				{
					String value = jsonEvent.getString("end");
					long time =  dateFormat.parse(value).getTime();
					intent.putExtra("endTime", time);
				}
				
				if (jsonEvent.has("description"))
				{
					String value = jsonEvent.getString("description");
					intent.putExtra("title", value);
				}
				
				if (jsonEvent.has("summary"))
				{
					String value = jsonEvent.getString("summary");
					intent.putExtra("description", value);
				}
				
				if (jsonEvent.has("location"))
				{
					String value = jsonEvent.getString("location");
					intent.putExtra("eventLocation", value);
				}
								
				runOnUiThread(new Runnable()
				{
					@Override
					public void run()
					{
						getContext().startActivity(intent);
						
						// TODO: Determine if intent started/failed and send error to bridge.
						
						if (activityListener != null)
						{
							activityListener.onLeavingApplication(MASTAdView.this);
						}
					}
				});
			}
			catch (Exception ex)
			{
				bridge.sendErrorMessage("Error parsing event data.", Consts.CommandCreateCalendarEvent);
			}
		}

		@Override
		public void mraidStorePicture(final Bridge bridge, String url)
		{
			if ((bridge != mraidBridge) && (bridge != mraidTwoPartBridge))
				return;
			
			if (TextUtils.isEmpty(url))
			{
				bridge.sendErrorMessage("Missing picture url.", Consts.CommandStorePicture);
				return;
			}
			
			if (featureSupportHandler != null)
			{
				if (featureSupportHandler.shouldStorePicture(MASTAdView.this, url) == false)
				{
					bridge.sendErrorMessage("Access denied.", Consts.CommandStorePicture);
					return;
				}
			}
			
			ImageRequest.create(Defaults.NETWORK_TIMEOUT_SECONDS, url, getUserAgent(), false, new ImageRequest.Handler()
			{
				@Override
				public void imageFailed(ImageRequest request, Exception ex)
				{
					 bridge.sendErrorMessage("Network error connecting to url.", Consts.CommandStorePicture);
					 
					 logEvent("Error obtaining photo request to save to camera roll.  Exception:" + ex, LogLevel.Error);
				}
				
				@Override
				public void imageReceived(ImageRequest request, Object imageObject)
				{
					// TODO: android.permission.WRITE_EXTERNAL_STORAGE
					final Bitmap bitmap = (Bitmap) imageObject;
					
					runOnUiThread(new Runnable()
					{
						@Override
						public void run()
						{
							String errorMessage = "Error saving picture to device.";
							
							try
							{
								String insertedUrl = MediaStore.Images.Media.insertImage(getContext().getContentResolver(), bitmap, "AdImage", "Image created by rich media ad.");
								if (TextUtils.isEmpty(insertedUrl))
								{
									bridge.sendErrorMessage(errorMessage, Consts.CommandStorePicture);
									
									logEvent(errorMessage, LogLevel.Error);
									return;
								}
								
								MediaScannerConnection.scanFile(getContext(), new String[] { insertedUrl }, null, null);
							}
							catch (Exception ex)
							{
								bridge.sendErrorMessage(errorMessage, Consts.CommandStorePicture);
								
								logEvent(errorMessage + " Exception:" + ex, LogLevel.Error);
							}
						}
					});
				}
			});
		}
	}

	private class AdRequestHandler implements AdRequest.Handler
	{
		@Override
		public void adRequestFailed(AdRequest request, Exception exception)
		{
			if (request != adRequest)
				return;
			
			logEvent("Ad request failed: " + exception, LogLevel.Error);
			
			if (requestListener != null)
			{
				requestListener.onFailedToReceiveAd(MASTAdView.this, exception);
			}
			
			adRequest = null;
		}

		@Override
		public void adRequestError(AdRequest request, String errorCode, String errorMessage)
		{
			if (request != adRequest)
				return;
			
			if (requestListener != null)
			{
				requestListener.onFailedToReceiveAd(MASTAdView.this, null);
			}
			
			LogLevel logLevel = LogLevel.Error;
			if (String.valueOf(404).equals(errorCode))
			{
				logLevel = LogLevel.Debug;
			}
			
			logEvent("Error response from server.  Error code: " + errorCode + ". Error message: " + errorMessage, logLevel);
			
			adRequest = null;
		}

		@Override
		public void adRequestCompleted(AdRequest request, AdDescriptor adDescriptor)
		{
			if (request != adRequest)
				return;
			
			renderAdDescriptor(adDescriptor);
			
			adRequest = null;
		}
	}
	
	private class ExpandDialog extends Dialog
	{
		private ViewGroup container = null;
		private View closeArea = null;
		
		public ExpandDialog(Context context)
		{
			super(context, android.R.style.Theme_NoTitleBar_Fullscreen);
			
			LayoutParams layoutParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
			container = new RelativeLayout(getContext());
			container.setBackgroundColor(0xff000000);
			setContentView(container, layoutParams);
			
			RelativeLayout.LayoutParams closeAreaLayoutParams =
					new RelativeLayout.LayoutParams(dpToPx(CloseAreaSizeDp), dpToPx(CloseAreaSizeDp));
			closeAreaLayoutParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
			closeAreaLayoutParams.addRule(RelativeLayout.ALIGN_PARENT_TOP);
			closeArea = new View(getContext());
			closeArea.setBackgroundColor(0x00000000);
			container.addView(closeArea, closeAreaLayoutParams);
			closeArea.setOnClickListener(new View.OnClickListener()
			{
				@Override
				public void onClick(View v)
				{
					dismiss();
				}
			});
			
			setOnDismissListener(new OnDismissListener()
			{
				// TODO: Resolve double close when ad invokes close (thus causing a dismiss and another close).
				// Possibly synchronize set/get state on the bridge.
				
				// TODO: Prevent back key dismissing while interstitial and button not displayed.
				
				@Override
				public void onDismiss(DialogInterface dialog)
				{
					if (mraidBridge != null)
					{
						switch (placementType)
						{
						case Inline:
							// TODO: What about two part?
							if (mraidBridge.getState() == State.Expanded)
							{
								mraidBridgeHandler.mraidClose(mraidBridge);
							}
							break;
							
						case Interstitial:
							mraidBridge.setState(State.Hidden);
							break;
						}
					}
				}
			});
		}

		protected void onStart()
		{
			super.onStart();

			switch (placementType)
			{
			case Inline:
				if (mraidTwoPartExpand == false)
				{
					updateMRAIDLayoutForState(mraidBridge, State.Expanded);
					mraidBridge.setState(State.Expanded);
				}
				break;
				
			case Interstitial:
				if (mraidBridge != null)
				{
					updateMRAIDLayoutForState(mraidBridge, State.Expanded);
					mraidBridge.setState(State.Default);
				}
				break;
			}
			
			closeArea.bringToFront();
			
			if (mraidBridge != null)
			{
				if (richMediaListener != null)
				{
					richMediaListener.onExpanded(MASTAdView.this);
				}
			}
			
			prepareCloseButton();
		}
		
		public void onBackPressed()
		{
			if (this == interstitialDialog)
			{
				if (closeArea.getBackground() == null)
				{
					// Don't allow close until the close button is available.
					return;
				}
			}
			
			super.onBackPressed();
		}
		
		public void addView(View view)
		{
			if (view.getParent() != container)
			{
				if (view.getParent() != null)
				{
					ViewGroup viewGroup = (ViewGroup) view.getParent();
					viewGroup.removeView(view);
				}
				
				container.addView(view);
			}
			
			closeArea.bringToFront();
		}
		
		public void removeView(View view)
		{
			container.removeView(view);
		}
		
		public void removeAllViews()
		{
			int childCount = container.getChildCount();
			for (int i = 0; i < childCount; ++i)
			{
				View child = container.getChildAt(i);
				if (child != closeArea)
				{
					container.removeView(child);
				}
			}
		}
		
		@SuppressWarnings("deprecation")
		public void setCloseImage(Drawable image)
		{
        	// Supporting API8 and higher.  Avoiding reflection for now.
			closeArea.setBackgroundDrawable(image);
		}
		
		@Override
		public void show()
		{
			super.show();
		}
	}
	
	private class LocationListener implements android.location.LocationListener
	{
		@Override
		public void onLocationChanged(Location location)
		{
			logEvent("LocationListener.onLocationChanged location:" + location.toString(), LogLevel.Debug);
			
			String lat = String.valueOf(location.getLatitude());
			String lng = String.valueOf(location.getLongitude());
				
			adRequestParameters.put("lat", lat);
			adRequestParameters.put("long", lng);
		}

		@Override
		public void onProviderDisabled(String provider)
		{
			logEvent("LocationListener.onProviderDisabled provider:" + provider, LogLevel.Debug);
		}

		@Override
		public void onProviderEnabled(String provider)
		{
			logEvent("LocationListener.onProviderEnabled provider:" + provider, LogLevel.Debug);
		}

		@Override
		public void onStatusChanged(String provider, int status, Bundle extras)
		{
			logEvent("LocationListener.onStatusChanged provider:" + provider + 
					" status:" + String.valueOf(status), LogLevel.Debug);
			
			if (status == LocationProvider.AVAILABLE)
				return;
			
			adRequestParameters.remove("lat");
			adRequestParameters.remove("long");
		}
	}
	
	private void logEvent(String event, LogLevel eventLevel)
	{
		if (eventLevel.ordinal() > logLevel.ordinal())
			return;
		
		if (logListener != null)
		{
			if (logListener.onLogEvent(this, event, eventLevel))
			{
				return;
			}
		}
		
		System.out.println(eventLevel + ":" + event); 
	}
	
	private final Activity getActivity()
	{
		Activity activity = null;
		
		Context context = getContext();
		if (context instanceof Activity)
		{
			activity = (Activity) context;
		}
		
		return activity;
	}

	protected final void runOnUiThread(final Runnable runnable)
	{
		if (runnable == null)
			return;
		
		Runnable uiRunnable = new Runnable()
		{
			public void run()
			{
				try
				{
					runnable.run();
				}
				catch (Exception ex)
				{
					logEvent("Exception during runOnUiThread:" + ex, LogLevel.Error);
				}
			}
		};

		Context ctx = getContext();
		if (ctx instanceof Activity)
		{
			Activity activity = (Activity) ctx;
			activity.runOnUiThread(uiRunnable);
		}
		else
		{
			logEvent("Context not instance of Activity, unable to run on UI thread.", LogLevel.Error);
		}
	}
	
	public int statusBarHeightDp()
	{
		View rootView = getRootView();
		
		int statusBarHeightDp = 25;
		if (rootView != null)
		{
			int resourceId = rootView.getResources().getIdentifier("status_bar_height", "dimen", "android");
        	if (resourceId > 0)
        		statusBarHeightDp = pxToDp(rootView.getResources().getDimensionPixelSize(resourceId));
		}
		
		return statusBarHeightDp;
	}
	
	public static int pxToDp(float px)
	{
		DisplayMetrics displayMetrics = Resources.getSystem().getDisplayMetrics();
		int dp = (int) (px / displayMetrics.density + .5f);
		return dp;
	}
	
	public static int dpToPx(int dp)
	{
		DisplayMetrics displayMetrics = Resources.getSystem().getDisplayMetrics();
		int px = (int) (dp * displayMetrics.density + .5f);
		return px;
	}
}