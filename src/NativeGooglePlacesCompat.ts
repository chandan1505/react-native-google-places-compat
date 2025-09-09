/**
 * TurboModule specification for React Native Google Places Compat
 *
 * This file defines the interface between JavaScript and native code.
 * It will be used by React Native's Codegen to generate platform-specific
 * native interface classes.
 */

import type { TurboModule } from 'react-native';
import { TurboModuleRegistry } from 'react-native';

export interface Spec extends TurboModule {
  /**
   * Initialize the Google Places client with API key and session configuration
   * @param apiKey - Google Places API key
   * @param sessionBasedAutocomplete - Whether to enable session-based autocomplete
   */
  initializePlaceClient(
    apiKey: string,
    sessionBasedAutocomplete: boolean
  ): void;

  /**
   * Open the native autocomplete modal/picker
   * @param options - Autocomplete options (filters, location bias, etc.)
   * @param placeFields - Array of place fields to return
   * @returns Promise resolving to selected Place object
   */
  openAutocompleteModal(
    options: Object,
    placeFields: Array<string>
  ): Promise<Object>;

  /**
   * Get autocomplete predictions for a query string
   * @param query - Search query string
   * @param options - Autocomplete options (filters, location bias, etc.)
   * @returns Promise resolving to array of AutocompletePrediction objects
   */
  getAutocompletePredictions(
    query: string,
    options: Object
  ): Promise<Array<Object>>;

  /**
   * Look up a place by its Place ID
   * @param placeID - Google Places Place ID
   * @param placeFields - Array of place fields to return
   * @returns Promise resolving to Place object
   */
  lookUpPlaceByID(placeID: string, placeFields: Array<string>): Promise<Object>;

  /**
   * Get current place(s) based on device location
   * @param placeFields - Array of place fields to return
   * @returns Promise resolving to array of CurrentPlace objects (with likelihood)
   */
  getCurrentPlace(placeFields: Array<string>): Promise<Array<Object>>;

  /**
   * Enable or disable session-based autocomplete
   * @param enabled - Whether to enable session tokens
   */
  setSessionBasedAutocomplete(enabled: boolean): void;

  /**
   * Refresh the current session token (if session-based autocomplete is enabled)
   */
  refreshSessionToken(): void;
}

export default TurboModuleRegistry.getEnforcing<Spec>('RNGooglePlaces');
