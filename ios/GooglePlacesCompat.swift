import Foundation
import GooglePlaces
import CoreLocation

@objc(RNGooglePlaces)
class RNGooglePlaces: NSObject, CLLocationManagerDelegate {

    static var instance: RNGooglePlaces?
    var token: GMSAutocompleteSessionToken? = nil
    var sessionBasedAutoCompleteEnabled: Bool = false
    var locationManager: CLLocationManager!

    override init() {
        super.init()
        RNGooglePlaces.instance = self
        locationManager = CLLocationManager()
        locationManager.delegate = self
    }

    deinit {
        locationManager.delegate = nil
    }

    @objc static func moduleName() -> String {
        return "RNGooglePlaces"
    }

    @objc static func requiresMainQueueSetup() -> Bool {
        return false
    }

    @objc func methodQueue() -> DispatchQueue {
        return DispatchQueue.main
    }

    @objc func initializePlaceClient(_ apiKey: String, sessionBasedAutocomplete: Bool) {
        GMSPlacesClient.provideAPIKey(apiKey)
        self.sessionBasedAutoCompleteEnabled = sessionBasedAutocomplete
        refreshSessionToken()
    }

    @objc func setSessionBasedAutocomplete(_ enabled: Bool) {
        sessionBasedAutoCompleteEnabled = enabled
        if !sessionBasedAutoCompleteEnabled {
            token = nil
        }
    }
    
    @objc func refreshSessionToken() {
        if (self.sessionBasedAutoCompleteEnabled) {
            token = GMSAutocompleteSessionToken.init()
        } else {
            token = nil
        }
    }

    @objc func openAutocompleteModal(_ options: NSDictionary, placeFields: NSArray, resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        let fields = placeFields as? [String] ?? []
        do {
            // Assume RNGooglePlacesViewController has been translated to Swift
            let acController = RNGooglePlacesViewController()

            let selectedFields = self.getSelectedFields(fields, isCurrentOrFetchPlace: false).map {$0.rawValue }
            let autocompleteFilter = GMSAutocompleteFilter()
            autocompleteFilter.types = options["types"] as? [String] ?? nil
            autocompleteFilter.countries = options["countries"] as? [String] ?? nil
            autocompleteFilter.locationBias = getLocationOption(from: options, forKey: "locationBias")
            autocompleteFilter.locationRestriction = getLocationOption(from: options, forKey: "locationRestriction")

//            acController.openAutocompleteModal(autocompleteFilter: autocompleteFilter,
//                                               placeFields: selectedFields,
//                                               resolver: resolve,
//                                               rejecter: reject)
        } catch let error as NSError {
            reject("E_OPEN_FAILED", "Could not open modal", error)
        }
    }

    @objc func getAutocompletePredictions(_ query: String, options: NSDictionary, resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        let filterOptions = options
        var autoCompleteSuggestionsList = [[String: Any]]()

        // Create the autocomplete filter
        let autocompleteFilter = GMSAutocompleteFilter()
        autocompleteFilter.types = filterOptions["types"] as? [String]
        autocompleteFilter.countries = filterOptions["countries"] as? [String]

        if let locationBias = getLocationOption(from: filterOptions, forKey: "locationBias") {
            autocompleteFilter.locationBias = locationBias
        }

        if let locationRestriction = getLocationOption(from: filterOptions, forKey: "locationRestriction") {
            autocompleteFilter.locationRestriction = locationRestriction
        }

        // Create the autocomplete request
        let request = GMSAutocompleteRequest(query: query)
        request.filter = autocompleteFilter
        request.sessionToken = token
        
        print("Fetching autocomplete predictions - filters: \(autocompleteFilter), sessionToken: \(String(describing: token))")

        // Fetch autocomplete suggestions
        GMSPlacesClient.shared().fetchAutocompleteSuggestions(from: request) { (results, error) in
            if let error = error {
                reject("E_AUTOCOMPLETE_ERROR", error.localizedDescription, error)
                return
            }

            if let results = results {
                for result in results {
                    var placeData = [String: Any]()
                    placeData["fullText"] = result.placeSuggestion?.attributedFullText.string
                    placeData["primaryText"] = result.placeSuggestion?.attributedPrimaryText.string
                    placeData["secondaryText"] = result.placeSuggestion?.attributedSecondaryText?.string
                    placeData["placeID"] = result.placeSuggestion?.placeID
                    placeData["types"] = result.placeSuggestion?.types

                    autoCompleteSuggestionsList.append(placeData)
                }

                resolve(autoCompleteSuggestionsList)
            } else {
                reject("E_AUTOCOMPLETE_ERROR", "No results found", nil)
            }
        }
    }

    @objc func lookUpPlaceByID(_ placeID: String, placeFields: NSArray, resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        let fields = placeFields as? [String] ?? []
        
        let selectedFields = getSelectedFields(fields, isCurrentOrFetchPlace: false).map {$0.rawValue }

        // Create the fetch place request
        let fetchPlaceRequest = GMSFetchPlaceRequest(placeID: placeID, placeProperties: selectedFields, sessionToken: token)

        print("Request to look up place by ID - \(placeID); Fields - \(fields); Parse fields: \(selectedFields); Session token: \(String(describing: token))")
              
        // Fetch the place details
        GMSPlacesClient.shared().fetchPlace(with: fetchPlaceRequest) { (place, error) in
            if let error = error {
                reject("E_PLACE_DETAILS_ERROR", error.localizedDescription, nil)
                return
            }

            if let place = place {
                resolve(place.toDictionary())
            } else {
                resolve([:])
            }
        }
        self.refreshSessionToken()
    }

    @objc func getCurrentPlace(_ placeFields: NSArray, resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        let fields = placeFields as? [String] ?? []
        self.locationManager.requestAlwaysAuthorization()

        let selectedFields = convertToPlaceField(getSelectedFields(fields, isCurrentOrFetchPlace: true))

        var likelyPlacesList = [NSDictionary]()

        GMSPlacesClient.shared().findPlaceLikelihoodsFromCurrentLocation(withPlaceFields: selectedFields) { (likelihoods, error) in
            if let error = error {
                reject("E_CURRENT_PLACE_ERROR", error.localizedDescription, nil)
                return
            }

            if let likelihoods = likelihoods {
                for likelihood in likelihoods {
                    var placeData = likelihood.place.toDictionary()
                    placeData["likelihood"] = NSNumber(value: likelihood.likelihood)

                    likelyPlacesList.append(placeData)
                }
            }

            resolve(likelyPlacesList)
        }
    }

    private func getSelectedFields(_ fields: [String], isCurrentOrFetchPlace currentOrFetch: Bool) -> [GMSPlaceProperty] {
        let fieldsMapping: [String: GMSPlaceProperty] = [
            "name": .name,
            "placeID": .placeID,
            "plusCode": .plusCode,
            "location": .coordinate,
            "openingHours": .openingHours,
            "phoneNumber": .phoneNumber,
            "address": .formattedAddress,
            "rating": .rating,
            "userRatingsTotal": .userRatingsTotal,
            "priceLevel": .priceLevel,
            "types": .types,
            "website": .website,
            "viewport": .viewport,
            "addressComponents": .addressComponents,
            "photos": .photos
        ]

        // Compute the fields for non-empty input
        var selectedFields: [GMSPlaceProperty] = []
        for fieldName in fields {
            if let fieldValue = fieldsMapping[fieldName] {
                // If currentOrFetch is true, exclude specific fields
                if currentOrFetch && [.openingHours, .phoneNumber, .website, .addressComponents].contains(fieldValue) {
                    continue
                }
                selectedFields.append(fieldValue)
            }
        }

        return selectedFields
    }
    
    // Intermediary function to convert [GMSPlaceProperty] to GMSPlaceField
    private func convertToPlaceField(_ properties: [GMSPlaceProperty]) -> GMSPlaceField {
        var placeField: GMSPlaceField = []
        
        // Create a mapping between GMSPlaceProperty and GMSPlaceField
        let propertyToFieldMapping: [GMSPlaceProperty: GMSPlaceField] = [
            .name: .name,
            .placeID: .placeID,
            .plusCode: .plusCode,
            .coordinate: .coordinate,
            .openingHours: .openingHours,
            .phoneNumber: .phoneNumber,
            .formattedAddress: .formattedAddress,
            .rating: .rating,
            .userRatingsTotal: .userRatingsTotal,
            .priceLevel: .priceLevel,
            .types: .types,
            .website: .website,
            .viewport: .viewport,
            .addressComponents: .addressComponents,
            .photos: .photos
        ]
        
        for property in properties {
            if let field = propertyToFieldMapping[property] {
                placeField = placeField.union(field)
            }
        }
        
        return placeField
    }


    private func errorFromException(exception: NSException) -> NSError {
        let exceptionInfo = [
            "name": exception.name,
            "reason": exception.reason ?? "",
            "callStackReturnAddresses": exception.callStackReturnAddresses,
            "callStackSymbols": exception.callStackSymbols,
            "userInfo": exception.userInfo ?? [:]
        ] as [String : Any]

        return NSError(domain: "RNGooglePlaces", code: 0, userInfo: exceptionInfo)
    }

    private func getLocationOption(from filterOptions: NSDictionary, forKey key: String) -> LocationOption? {
        guard let locationData = filterOptions[key] as? [String: Double] else {
            return nil
        }

        let latitudeNE = locationData["latitudeNE"] ?? 0.0
        let longitudeNE = locationData["longitudeNE"] ?? 0.0
        let latitudeSW = locationData["latitudeSW"] ?? 0.0
        let longitudeSW = locationData["longitudeSW"] ?? 0.0

        // Check that the coordinates are valid before creating the bounds
        if latitudeNE != 0, longitudeNE != 0, latitudeSW != 0, longitudeSW != 0 {
            let neBoundsCorner = CLLocationCoordinate2D(latitude: latitudeNE, longitude: longitudeNE)
            let swBoundsCorner = CLLocationCoordinate2D(latitude: latitudeSW, longitude: longitudeSW)

            return GMSPlaceRectangularLocationOption(neBoundsCorner, swBoundsCorner)
        }

        return nil
    }
}
