// TurboModule implementation for Google Places Compat
import NativeGooglePlacesCompat from './NativeGooglePlacesCompat';
import type {
  GMSTypes,
  PlaceFields,
  CurrentPlace,
  RNGooglePlacesNativeOptions,
} from './types';

const LOCATION_ONLY_FIELDS: PlaceFields[] = [
  'addressComponents',
  'address',
  'location',
  'plusCode',
  'types',
  'viewport',
];

class RNGooglePlaces {
  static optionsDefaults: RNGooglePlacesNativeOptions = {
    type: null,
    types: null,
    country: '',
    countries: null,
    useOverlay: false,
    initialQuery: '',
    locationBias: {
      latitudeSW: 0,
      longitudeSW: 0,
      latitudeNE: 0,
      longitudeNE: 0,
    },
    locationRestriction: {
      latitudeSW: 0,
      longitudeSW: 0,
      latitudeNE: 0,
      longitudeNE: 0,
    },
  };

  static placeFieldsDefaults: (keyof GMSTypes.Place)[] = [];

  initializePlaceClient(
    apiKey: string,
    sessionBasedAutocomplete: boolean = false
  ) {
    return NativeGooglePlacesCompat.initializePlaceClient(
      apiKey,
      sessionBasedAutocomplete
    );
  }

  openAutocompleteModal(
    options: Partial<RNGooglePlacesNativeOptions> = {},
    placeFields: PlaceFields[] = []
  ): Promise<GMSTypes.Place> {
    return NativeGooglePlacesCompat.openAutocompleteModal(
      {
        ...RNGooglePlaces.optionsDefaults,
        ...options,
      },
      [...RNGooglePlaces.placeFieldsDefaults, ...placeFields]
    ) as Promise<GMSTypes.Place>;
  }

  getAutocompletePredictions(
    query: string,
    options: Partial<RNGooglePlacesNativeOptions> = {}
  ): Promise<GMSTypes.AutocompletePrediction[]> {
    return NativeGooglePlacesCompat.getAutocompletePredictions(query, {
      ...RNGooglePlaces.optionsDefaults,
      ...options,
    }) as Promise<GMSTypes.AutocompletePrediction[]>;
  }

  lookUpPlaceByID(
    placeID: string,
    placeFields: PlaceFields[] = []
  ): Promise<GMSTypes.Place> {
    return NativeGooglePlacesCompat.lookUpPlaceByID(placeID, [
      ...RNGooglePlaces.placeFieldsDefaults,
      ...placeFields,
    ]) as Promise<GMSTypes.Place>;
  }

  getCurrentPlace(placeFields: PlaceFields[] = []): Promise<CurrentPlace[]> {
    return NativeGooglePlacesCompat.getCurrentPlace([
      ...RNGooglePlaces.placeFieldsDefaults,
      ...placeFields,
    ]) as Promise<CurrentPlace[]>;
  }

  setSessionBasedAutocomplete(enabled: boolean) {
    NativeGooglePlacesCompat.setSessionBasedAutocomplete(enabled);
    this.refreshSessionToken();
  }

  refreshSessionToken() {
    return NativeGooglePlacesCompat.refreshSessionToken();
  }
}

const RNGooglePlacesCompat = new RNGooglePlaces();
export type { GMSTypes };
export { LOCATION_ONLY_FIELDS };
export default RNGooglePlacesCompat;
