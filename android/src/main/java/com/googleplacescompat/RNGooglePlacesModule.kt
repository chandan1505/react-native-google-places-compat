package com.googleplacescompat

import android.Manifest.permission
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.text.TextUtils
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.facebook.react.bridge.ActivityEventListener
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.ReadableArray
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.WritableMap
import com.facebook.react.bridge.WritableNativeArray
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.model.PlaceLikelihood
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FetchPlaceResponse
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsResponse
import com.google.android.libraries.places.api.net.FindCurrentPlaceRequest
import com.google.android.libraries.places.api.net.FindCurrentPlaceResponse
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.android.libraries.places.widget.Autocomplete
import com.google.android.libraries.places.widget.AutocompleteActivity
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode
import com.googleplacescompat.RNGooglePlacesPlaceFieldEnum.Companion.findByFieldKey
import com.googleplacescompat.RNGooglePlacesPlaceTypeEnum.Companion.findByTypeId

class RNGooglePlacesModule(private val reactContext: ReactApplicationContext) :
  NativeGooglePlacesCompatSpec(reactContext), ActivityEventListener {

  private var pendingPromise: Promise? = null
  private var lastSelectedFields: List<Place.Field>? = null
  private lateinit var placesClient: PlacesClient
  private var sessionToken: AutocompleteSessionToken? = null
  private var sessionBasedAutoCompleteEnabled = false

  override fun getName(): String {
    return REACT_CLASS
  }

  @ReactMethod
  override fun initializePlaceClient(apiKey: String, sessionBasedAutocomplete: Boolean) {
    if (!Places.isInitialized() && apiKey.isNotBlank()) {
      Places.initializeWithNewPlacesApiEnabled(reactContext.applicationContext, apiKey)
      placesClient = Places.createClient(reactContext.applicationContext)
      reactContext.addActivityEventListener(this)
      sessionBasedAutoCompleteEnabled = sessionBasedAutocomplete
      refreshSessionToken()
    }
  }

  /**
   * Called after the autocomplete activity has finished to return its result.
   */
  override fun onActivityResult(
    activity: Activity,
    requestCode: Int,
    resultCode: Int,
    intent: Intent?
  ) {

    // Check that the result was from the autocomplete widget.
    if (requestCode == AUTOCOMPLETE_REQUEST_CODE && intent != null) {
      when (resultCode) {
        AutocompleteActivity.RESULT_OK -> {
          val place = Autocomplete.getPlaceFromIntent(intent)
          val map = propertiesMapForPlace(place, lastSelectedFields)
          resolvePromise(map)
        }

        AutocompleteActivity.RESULT_ERROR -> {
          val status = Autocomplete.getStatusFromIntent(intent)
          rejectPromise("E_RESULT_ERROR", Error(status.statusMessage))
        }

        AutocompleteActivity.RESULT_CANCELED -> {
          rejectPromise("E_USER_CANCELED", Error("Search cancelled"))
        }
      }
    }
  }

  /**
   * Exposed React's methods
   */

  @ReactMethod
  override fun refreshSessionToken() {
    if (sessionBasedAutoCompleteEnabled) {
      sessionToken = AutocompleteSessionToken.newInstance()
    }
  }

  @ReactMethod
  override fun setSessionBasedAutocomplete(enabled: Boolean) {
    sessionBasedAutoCompleteEnabled = enabled
  }

  @ReactMethod
  override fun openAutocompleteModal(options: ReadableMap, fields: ReadableArray, promise: Promise) {
    if (!Places.isInitialized()) {
      promise.reject(
        "E_API_KEY_ERROR",
        Error("No API key defined in gradle.properties or errors initializing Places")
      )
      return
    }
    val currentActivity = currentActivity
    if (currentActivity == null) {
      promise.reject("E_ACTIVITY_DOES_NOT_EXIST", Error("Activity doesn't exist"))
      return
    }
    pendingPromise = promise
    lastSelectedFields = ArrayList()

    val typedOptions = options.getAutoCompletePredictionsOptions()

    lastSelectedFields = getPlaceFields(fields.toArrayList(), false)
    val autocompleteIntent = Autocomplete.IntentBuilder(
      if (typedOptions.useOverlay) AutocompleteActivityMode.OVERLAY else AutocompleteActivityMode.FULLSCREEN,
      lastSelectedFields!!
    )

    if (typedOptions.isLocationBiasSet()) {
      autocompleteIntent.setLocationBias(typedOptions.locationBias?.toRectangularBounds())
    }

    if (typedOptions.isLocationRestrictionSet()) {
      autocompleteIntent.setLocationRestriction(typedOptions.locationRestriction?.toRectangularBounds())
    }

    autocompleteIntent.setCountries(typedOptions.countries)
    typedOptions.initialQuery?.let {
      autocompleteIntent.setInitialQuery(it)
    }

    autocompleteIntent.setTypesFilter(typedOptions.filterTypes
    )
    currentActivity.startActivityForResult(
      autocompleteIntent.build(reactContext.applicationContext),
      AUTOCOMPLETE_REQUEST_CODE
    )
  }

  @ReactMethod
  override fun getAutocompletePredictions(query: String, options: ReadableMap, promise: Promise) {
    pendingPromise = promise
    if (!Places.isInitialized()) {
      promise.reject(
        "E_API_KEY_ERROR",
        Error("No API key defined in gradle.properties or errors initializing Places")
      )
      return
    }

    val typedOptions = options.getAutoCompletePredictionsOptions()
    val requestBuilder = FindAutocompletePredictionsRequest.builder()
      .setQuery(query)
    requestBuilder.typesFilter = typedOptions.filterTypes
    requestBuilder.countries = typedOptions.countries

    if (typedOptions.isLocationBiasSet()) {
      requestBuilder.locationBias = typedOptions.locationBias?.toRectangularBounds()
    }

    if (typedOptions.isLocationRestrictionSet()) {
      requestBuilder.locationRestriction = typedOptions.locationRestriction?.toRectangularBounds()
    }

    if (sessionToken != null && sessionBasedAutoCompleteEnabled) {
      requestBuilder.sessionToken = sessionToken
    }

    Log.d(TAG, "getAutocompletePredictions: with options: $typedOptions\n sessionToken: $sessionToken")
    val task = placesClient.findAutocompletePredictions(requestBuilder.build())
    task.addOnSuccessListener { response: FindAutocompletePredictionsResponse ->
      if (response.autocompletePredictions.size == 0) {
        val emptyResult = Arguments.createArray()
        promise.resolve(emptyResult)
        return@addOnSuccessListener
      }
      val predictionsList = Arguments.createArray()
      for (prediction in response.autocompletePredictions) {
        val map = Arguments.createMap()
        map.putString("fullText", prediction.getFullText(null).toString())
        map.putString("primaryText", prediction.getPrimaryText(null).toString())
        map.putString("secondaryText", prediction.getSecondaryText(null).toString())
        map.putString("placeID", prediction.placeId.toString())
        if (prediction.types.size > 0) {
          map.putArray("types", Arguments.fromArray(prediction.types.toTypedArray()))
        }
        predictionsList.pushMap(map)
      }
      promise.resolve(predictionsList)
    }
    task.addOnFailureListener { exception: Exception ->
      Log.e(TAG, "getAutocompletePredictions: ", exception)
      promise.reject(
        "E_AUTOCOMPLETE_ERROR",
        Error(exception.message)
      )
    }
  }

  @ReactMethod
  override fun lookUpPlaceByID(placeID: String, fields: ReadableArray, promise: Promise) {
    pendingPromise = promise
    if (!Places.isInitialized()) {
      promise.reject(
        "E_API_KEY_ERROR",
        Error("No API key defined in gradle.properties or errors initializing Places")
      )
      return
    }
    val selectedFields = getPlaceFields(fields.toArrayList(), false)
    val builder = FetchPlaceRequest.builder(placeID, selectedFields)
    if (sessionToken != null && sessionBasedAutoCompleteEnabled) {
      builder.sessionToken = sessionToken
    }

    Log.d(TAG, "lookUpPlaceByID: with placeID: $placeID \n fields: $selectedFields \n sessionToken: $sessionToken")

    placesClient.fetchPlace(builder.build()).addOnSuccessListener { response: FetchPlaceResponse ->
      val place = response.place
      val map = propertiesMapForPlace(place, selectedFields)
      promise.resolve(map)
    }.addOnFailureListener { exception: Exception ->
      Log.e(TAG, "lookUpPlaceByID: ", exception)
      promise.reject(
        "E_PLACE_DETAILS_ERROR",
        Error(exception.message)
      )
    }

    if (sessionBasedAutoCompleteEnabled) {
      // end session after the request
      refreshSessionToken()
    }
  }

  @ReactMethod
  @RequiresPermission(allOf = [permission.ACCESS_FINE_LOCATION, permission.ACCESS_WIFI_STATE])
  override fun getCurrentPlace(fields: ReadableArray, promise: Promise) {
    if (ContextCompat.checkSelfPermission(
        reactContext.applicationContext,
        permission.ACCESS_WIFI_STATE
      )
      != PackageManager.PERMISSION_GRANTED
      || ContextCompat.checkSelfPermission(
        reactContext.applicationContext,
        permission.ACCESS_FINE_LOCATION
      )
      != PackageManager.PERMISSION_GRANTED
    ) {
      promise.reject(
        "E_CURRENT_PLACE_ERROR",
        Error("Both ACCESS_WIFI_STATE & ACCESS_FINE_LOCATION permissions are required")
      )
      return
    }
    val selectedFields = getPlaceFields(fields.toArrayList(), true)
    if (checkPermission(permission.ACCESS_FINE_LOCATION)) {
      findCurrentPlaceWithPermissions(selectedFields, promise)
    }
  }

  /**
   * Fetches a list of [PlaceLikelihood] instances that represent the Places the user is
   * most
   * likely to be at currently.
   */
  @RequiresPermission(allOf = [permission.ACCESS_FINE_LOCATION, permission.ACCESS_WIFI_STATE])
  private fun findCurrentPlaceWithPermissions(
    selectedFields: List<Place.Field>,
    promise: Promise
  ) {
    val currentPlaceRequest = FindCurrentPlaceRequest.newInstance(selectedFields)
    val currentPlaceTask = placesClient.findCurrentPlace(currentPlaceRequest)
    currentPlaceTask.addOnSuccessListener { response: FindCurrentPlaceResponse ->
      if (response.placeLikelihoods.size == 0) {
        val emptyResult = Arguments.createArray()
        promise.resolve(emptyResult)
        return@addOnSuccessListener
      }
      val likelyPlacesList = Arguments.createArray()
      for (placeLikelihood in response.placeLikelihoods) {
        val map = propertiesMapForPlace(placeLikelihood.place, selectedFields)
        map.putDouble("likelihood", placeLikelihood.likelihood)
        likelyPlacesList.pushMap(map)
      }
      promise.resolve(likelyPlacesList)
    }
    currentPlaceTask.addOnFailureListener { exception: Exception ->
      Log.e(TAG, "findCurrentPlaceWithPermissions: ", exception)
      promise.reject(
        "E_CURRENT_PLACE_ERROR",
        Error(exception.message)
      )
    }
  }

  private fun propertiesMapForPlace(
    place: Place,
    selectedFields: List<Place.Field>?
  ): WritableMap {
    // Display attributions if required.
    // CharSequence attributions = place.getAttributions();
    val map = Arguments.createMap()
    if (selectedFields == null) return map
    if (selectedFields.contains(Place.Field.LAT_LNG)) {
      place.latLng?.let {
        val locationMap = Arguments.createMap()
        locationMap.putDouble("latitude", it.latitude)
        locationMap.putDouble("longitude", it.longitude)
        map.putMap("location", locationMap)
      }
    }
    if (selectedFields.contains(Place.Field.NAME)) {
      map.putString("name", place.name)
    }
    if (selectedFields.contains(Place.Field.ADDRESS)) {
      if (!TextUtils.isEmpty(place.address)) {
        map.putString("address", place.address)
      } else {
        map.putString("address", "")
      }
    }
    if (selectedFields.contains(Place.Field.ADDRESS_COMPONENTS)) {
      if (place.addressComponents != null) {
        val items = place.addressComponents?.asList() ?: listOf()
        val addressComponents = WritableNativeArray()
        for (item in items) {
          val addressComponentMap = Arguments.createMap()
          addressComponentMap.putArray("types", Arguments.fromList(item.types))
          addressComponentMap.putString("name", item.name)
          addressComponentMap.putString("shortName", item.shortName)
          addressComponents.pushMap(addressComponentMap)
        }
        map.putArray("addressComponents", addressComponents)
      } else {
        val emptyResult = Arguments.createArray()
        map.putArray("addressComponents", emptyResult)
      }
    }
    if (selectedFields.contains(Place.Field.PHONE_NUMBER)) {
      if (!TextUtils.isEmpty(place.phoneNumber)) {
        map.putString("phoneNumber", place.phoneNumber)
      } else {
        map.putString("phoneNumber", "")
      }
    }
    if (selectedFields.contains(Place.Field.WEBSITE_URI)) {
      place.websiteUri?.let {
        map.putString("website", it.toString())
      } ?: map.putString("website", "")
    }
    if (selectedFields.contains(Place.Field.ID)) {
      map.putString("placeID", place.id)
    }
    place.attributions?.let { attributions ->
      map.putArray("attributions", Arguments.fromArray(ArrayList(attributions).toTypedArray()))
    } ?: {
      val emptyResult = Arguments.createArray()
      map.putArray("attributions", emptyResult)
    }
    if (selectedFields.contains(Place.Field.TYPES)) {
      place.placeTypes?.let {
        map.putArray("types", Arguments.fromArray(it.toTypedArray()))
      } ?: {
        val emptyResult = Arguments.createArray()
        map.putArray("types", emptyResult)
      }
    }
    if (selectedFields.contains(Place.Field.VIEWPORT)) {
      place.viewport?.let {
        val viewportMap = Arguments.createMap()
        viewportMap.putDouble("latitudeNE", it.northeast.latitude)
        viewportMap.putDouble("longitudeNE", it.northeast.longitude)
        viewportMap.putDouble("latitudeSW", it.southwest.latitude)
        viewportMap.putDouble("longitudeSW", it.southwest.longitude)
        map.putMap("viewport", viewportMap)
      } ?: {
        val emptyResult = Arguments.createMap()
        map.putMap("viewport", emptyResult)
      }
    }
    if (selectedFields.contains(Place.Field.PRICE_LEVEL)) {
      map.putInt("priceLevel", place.priceLevel ?: 0)

    }
    if (selectedFields.contains(Place.Field.RATING)) {
      map.putDouble("rating", place.rating ?: 0.0)
    }
    if (selectedFields.contains(Place.Field.OPENING_HOURS)) {
      place.openingHours?.let {
        map.putArray(
          "openingHours",
          Arguments.fromArray(ArrayList(it.weekdayText).toTypedArray())
        )
      } ?: {
        val emptyResult = Arguments.createArray()
        map.putArray("openingHours", emptyResult)
      }
    }
    if (selectedFields.contains(Place.Field.PLUS_CODE)) {
      place.plusCode?.let { plusCode ->
        val plusCodeMap = Arguments.createMap()
        plusCodeMap.putString("compoundCode", plusCode.compoundCode)
        plusCodeMap.putString("globalCode", plusCode.globalCode)
        map.putMap("plusCode", plusCodeMap)
      } ?: {
        val emptyResult = Arguments.createMap()
        map.putMap("plusCode", emptyResult)
      }
    }
    if (selectedFields.contains(Place.Field.USER_RATINGS_TOTAL)) {
      map.putInt("userRatingsTotal", place.userRatingsTotal ?: 0)
    }
    return map
  }

  private fun getPlaceFields(
    placeFields: ArrayList<Any>,
    isCurrentOrFetchPlace: Boolean
  ): List<Place.Field> {
    val selectedFields: MutableList<Place.Field> = ArrayList()
    if (placeFields.size == 0 && !isCurrentOrFetchPlace) {
      return Place.Field.values().toList()
    }
    if (placeFields.size == 0) {
      val allPlaceFields: MutableList<Place.Field> =
        ArrayList(Place.Field.values().toList())
      allPlaceFields.removeAll(
        listOf(
          Place.Field.OPENING_HOURS,
          Place.Field.PHONE_NUMBER,
          Place.Field.WEBSITE_URI,
          Place.Field.ADDRESS_COMPONENTS
        )
      )
      return allPlaceFields
    }
    for (placeField in placeFields) {
      if (findByFieldKey(placeField.toString()) != null) {
        selectedFields.add(findByFieldKey(placeField.toString())!!.field)
      }
    }
    if (placeFields.size != 0 && isCurrentOrFetchPlace) {
      selectedFields.removeAll(
        listOf(
          Place.Field.OPENING_HOURS,
          Place.Field.PHONE_NUMBER,
          Place.Field.WEBSITE_URI,
          Place.Field.ADDRESS_COMPONENTS
        )
      )
    }
    return selectedFields
  }

  private fun checkPermission(permission: String): Boolean {
    val currentActivity = currentActivity
    val hasPermission = ContextCompat.checkSelfPermission(
      reactContext.applicationContext,
      permission
    ) == PackageManager.PERMISSION_GRANTED
    if (!hasPermission && currentActivity != null) {
      ActivityCompat.requestPermissions(currentActivity, arrayOf(permission), 0)
    }
    return hasPermission
  }

  private fun rejectPromise(code: String, err: Error) {
    pendingPromise?.reject(code, err)
    pendingPromise = null
  }

  private fun resolvePromise(data: Any) {
    pendingPromise?.resolve(data)
    this.pendingPromise = null
  }

  private fun findPlaceTypeLabelByPlaceTypeId(id: Int): String {
    return findByTypeId(id).label
  }

  override fun onNewIntent(intent: Intent) {}

  companion object {
    const val TAG = "RNGooglePlaces"
    var AUTOCOMPLETE_REQUEST_CODE = 360
    var REACT_CLASS = "RNGooglePlaces"
  }
}
