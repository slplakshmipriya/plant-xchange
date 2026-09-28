package com.gardenswap.app.idv;

import android.app.Activity;

import com.gardenswap.app.api.IdvStatus;

/**
 * Identity-verification provider abstraction (AND-011).
 *
 * <p>The real provider SDK is not bundled yet. {@link StubIdvProvider}
 * implements this interface for development and the Wave 1 integration
 * checkpoint; swapping in the real SDK means replacing the implementation
 * only — this interface stays unchanged.
 *
 * <h2>PRODUCTION INTEGRATION NOTES — Stripe Identity</h2>
 *
 * <p>To go live with Stripe Identity, the following is needed; none of it is
 * wired yet:
 *
 * <ol>
 *   <li><b>Stripe publishable key.</b> Configure via {@code BuildConfig} (a
 *       {@code buildConfigField "String", "STRIPE_PUBLISHABLE_KEY"} in
 *       {@code app/build.gradle}, injected from the environment — never commit
 *       a real key) or as manifest meta-data
 *       ({@code <meta-data android:name="com.gardenswap.stripe.PublishableKey"
 *       android:value="..."/>}). No real key exists anywhere in this repo.
 *   <li><b>Backend session endpoint.</b> The backend must expose
 *       {@code POST /v1/idv/session} (API-012) which creates a Stripe
 *       VerificationSession server-side using the secret key and returns its
 *       id and client secret (e.g. {@code {"sessionId": "...",
 *       "clientSecret": "..."}}). The app-side
 *       {@code GardenSwapApi.createIdvSession} will then pass the returned
 *       client secret to the real provider instead of the stub session id.
 *   <li><b>Stripe Identity SDK artifact.</b> Add to {@code app/build.gradle}
 *       (NOT added yet — do not add it until keys exist):
 *       <pre>{@code
 *       implementation "com.stripe:stripe-identity:21.7.0"
 *       }</pre>
 *   <li><b>Webhook handling.</b> The backend must receive Stripe's
 *       {@code identity.verification_session.verified} /
 *       {@code ...requires_input} events on a webhook endpoint, verify the
 *       signature, and persist the terminal status so
 *       {@code getIdvStatus} reflects it. The client-side
 *       {@code IdvActivity.syncMockStatus} path goes away with the mock.
 *   <li><b>The swap is one line.</b> In {@link IdvActivity}, replace
 *       {@code new StubIdvProvider()} with the real implementation
 *       (e.g. {@code new StripeIdvProvider(publishableKey)}); nothing else
 *       changes — {@link StubIdvActivity} is then dead code and can be
 *       deleted.
 * </ol>
 */
public interface IdvProvider {

    /** Exactly one method fires per {@link #start} call. */
    interface Listener {
        void onResult(IdvStatus status);

        void onCanceled();
    }

    /**
     * Launch the provider's verification flow for {@code sessionId}.
     *
     * @param activity  host activity, used to start the provider UI
     * @param sessionId opaque session from {@code GardenSwapApi.createIdvSession}
     * @param listener  receives the terminal verification status
     */
    void start(Activity activity, String sessionId, Listener listener);
}
