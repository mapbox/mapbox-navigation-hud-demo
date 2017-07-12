# mapbox-navigation-hud-demo
Example heads-up display application for Android using Mapbox Navigation SDK 

To get started, you need two things:
1. Google Places API Key https://developers.google.com/places/web-service/get-api-key
     - Add your API key to the `AndroidManifest.xml`:
         ```xml
         <meta-data
            android:name="com.google.android.geo.API_KEY"
            android:value="ADD_YOUR_GOOGLE_API_KEY"/>
         ```
2. Mapbox Access Token - create an account https://www.mapbox.com/signup/
    - Add your access token to `Constants.java`:
      ```java
      public static final String MAPBOX_ACCESS_TOKEN = "";
      ```
      
Once you have both of those added to the project, you're good to build! 
