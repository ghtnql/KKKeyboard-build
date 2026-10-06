/// Selects real versus simulated ad execution.
///
/// Debug builds simulate ads so tests never touch Google Mobile Ads or the
/// consent form. Release (including TestFlight) builds use the real controllers.
enum AdExecutionMode {
    static var simulatesAds: Bool {
        #if DEBUG
        return true
        #else
        return false
        #endif
    }
}
