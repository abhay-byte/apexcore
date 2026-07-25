
## Google Play Store — Policy & Guideline Compliance Research

**Purpose:** A consolidated reference of Google Play's official Developer Program Policies and publishing requirements, compiled from Google's own documentation (support.google.com, developer.android.com, play.google). Use this as a checklist to validate that an app is compliant before submission.
**Compiled:** July 25, 2026 | **Primary source:** [Developer Program Policy — effective May 27, 2026](https://support.google.com/googleplay/android-developer/answer/17105854?hl=en)
---

## 0. How to Use This Document

For each app being reviewed, go section by section and confirm:
- [ ] Nothing in **Section 3 (Restricted Content)** applies to your app, or if it does, that you meet the special requirements (age-gating, licensing, declarations).
- [ ] **Section 4 (Privacy/Data/Permissions)** is fully satisfied — privacy policy, Data safety form, permission justifications.
- [ ] **Section 5 (Deception/Malware/Impersonation)** — nothing hidden, nothing misleading, nothing malicious.
- [ ] **Section 6 (Monetization/Ads)** — billing and ad placement rules are followed.
- [ ] **Section 7 (Store Listing)** — metadata, screenshots, and content rating are accurate.
- [ ] **Section 8 (Spam/Minimum Functionality)** — app is stable and has real utility.
- [ ] **Section 9 (Families)** — required only if targeting children.
- [ ] **Section 10 (Technical/Account requirements)** — account verification, AAB format, target API level, testing requirements are met.
---

## 1. Quick Pre-Submission Checklist

| Area | Requirement | Source |
|---|---|---|
| Build format | Upload an **Android App Bundle (.aab)**, not a raw APK, signed with **Play App Signing** | [App Bundle](https://developer.android.com/guide/app-bundle) |
| Target API level | New apps/updates must target API level within **1 year of latest major Android release** (currently API 36 required from Aug 31, 2026; API 35 is floor today) | [Target API Level Policy](https://support.google.com/googleplay/android-developer/answer/16561298) · [timelines](https://support.google.com/googleplay/android-developer/answer/11926878) |
| 64-bit support | Apps with native (NDK/.so) code must ship 64-bit binaries | [64-bit requirement](https://developer.android.com/google/play/requirements/64-bit) |
| Developer account | Personal (gov ID) or Organization (D-U-N-S number) — verified identity | [Verify identity](https://support.google.com/googleplay/android-developer/answer/10841920) |
| Android Developer Verification | Separate, newer requirement: register your identity + your app's signing key with Android (not just Play). Enforcement begins **Sept 2026** in select regions | [developer.android.com/developer-verification](https://developer.android.com/developer-verification) |
| Closed testing (new personal accounts) | 12 opted-in testers, 14 continuous days, before requesting production access (orgs exempt) | [Testing requirements](https://support.google.com/googleplay/android-developer/answer/14151465) |
| Privacy Policy | Public URL (no PDF, no geofencing) in Play Console AND linked/shown in-app | [User Data policy](https://support.google.com/googleplay/android-developer/answer/10144311) |
| Data safety form | Completed and accurate for every app | [Data safety](https://support.google.com/googleplay/android-developer/answer/10787469) |
| Account deletion | If app supports account creation, must offer in-app AND web account deletion | [Account deletion](https://support.google.com/googleplay/android-developer/answer/13327111) |
| Content rating | IARC questionnaire completed truthfully | [Content Ratings](https://support.google.com/googleplay/android-developer/answer/9898843) |
| Target audience declaration | Accurate age-group targeting in App content page | [Families Policy](https://support.google.com/googleplay/android-developer/answer/9893335) |
| Ads (if any) | Non-deceptive, non-disruptive, appropriate for content rating | [Ads policy](https://support.google.com/googleplay/android-developer/answer/9857753) |
| Payments | Digital goods/subscriptions must use Google Play Billing (with narrow exceptions) | [Payments policy](https://support.google.com/googleplay/android-developer/answer/9858738) |
| External code execution | No downloading/executing dex, JAR, or native `.so` code from outside Google Play | [Device and Network Abuse](https://support.google.com/googleplay/android-developer/answer/16559646) |
| Installing/linking other apps' APKs | No installing other apps without explicit consent; no acting as a "hostile downloader" (bundling/linking non-Play APKs) unless you're a genuine browser/file-sharing app with user-initiated downloads only | [Hostile Downloaders](https://support.google.com/googleplay/android-developer/answer/11189134) |
| Third-party API compliance (e.g. YouTube playback) | App must not access/use any API in a way that violates that API's own Terms of Service (e.g., no stripping YouTube ads or bypassing YouTube playback restrictions) | [Device and Network Abuse](https://support.google.com/googleplay/android-developer/answer/16559646) |
| Disabling device security | No requesting/tricking users into turning off Google Play Protect or other device security protections | [Mobile Unwanted Software](https://support.google.com/googleplay/android-developer/answer/9970222) |
---

## 2. Master Link Directory (Official Sources)

- [ ] Full Developer Program Policy (all sections): https://support.google.com/googleplay/android-developer/answer/17105854
- [ ] Policy Center home: https://support.google.com/googleplay/android-developer/topic/9858052
- [ ] Public quick-reference policy hub: https://play.google/developer-content-policy/
- [ ] Developer Distribution Agreement: https://play.google.com/about/developer-distribution-agreement.html
- [ ] Policy announcements / changelog: https://support.google.com/googleplay/android-developer/announcements/13412212
- [ ] Play Academy (policy training courses): https://playacademy.withgoogle.com/courses/google-play-policy
- [ ] Android Developer Verification hub: https://developer.android.com/developer-verification
- [ ] Core App Quality Guidelines: https://developer.android.com/quality
---

## 3. Restricted Content — What Your App Must NOT Contain

Source: [Restricted Content policies](https://support.google.com/googleplay/android-developer/topic/9877466)

## 3.1 Child Endangerment & Child Sexual Abuse Material (CSAM)

**Absolute zero-tolerance.** Immediate removal + possible law-enforcement referral.
- [ ] No content facilitating exploitation/abuse of children: grooming, sexualization of minors, sextortion, trafficking.
- [ ] Apps appealing to children may not contain adult themes, excessive violence/gore, or content promoting harmful activities.
- [ ] No content promoting negative body image (e.g., depicting plastic surgery/weight loss for entertainment aimed at children).
- [ ] **Social and Dating apps** must additionally self-certify **Child Safety Standards**: published anti-CSAE policy, in-app reporting mechanism, a CSAM-response process, legal compliance, and a designated Child Safety point of contact.
- [ ] Source: [Child Endangerment](https://support.google.com/googleplay/android-developer/answer/9878809) · [Child Safety Standards](https://support.google.com/googleplay/android-developer/answer/14747720)
- [ ] To report CSAM: https://support.google.com/googleplay/contact/rap_family

## 3.2 Sexual Content & Profanity

- [ ] **Don't:** pornography, content intended to be sexually gratifying, solicitation of sexual acts for compensation ("sugar dating," escort services), non-consensual sexual content, deepfakes, "undress" apps, bestiality, profanity/explicit text in listings.
- [ ] **Do (limited exception):** nudity may be allowed only if primarily educational/documentary/scientific/artistic (EDSA) and non-gratuitous. Catalog apps (ebook/video listing apps) may include sexual-content titles only as a minor fraction of catalog, without active promotion, and with minor-protection restrictions.
- [ ] Source: [Inappropriate Content — Sexual Content and Profanity](https://support.google.com/googleplay/android-developer/answer/9878810)

## 3.3 Hate Speech

- [ ] No content inciting hatred/violence against protected groups (race, ethnicity, religion, disability, age, nationality, veteran status, sexual orientation, gender/gender identity, caste, immigration status).
- [ ] No hateful slurs, dehumanizing claims, or hate-group symbols/paraphernalia.

## 3.4 Violence & Violent Extremism

- [ ] No gratuitous/realistic violence, violent threats. Fictional/cartoon violence in games is generally fine.
- [ ] No content promoting self-harm, suicide, eating disorders, or "choking games."
- [ ] No terrorist/violent-extremist organizations, recruitment, or glorification of violence against civilians (even with EDSA framing, context must be clear).

## 3.5 Sensitive Events

- [ ] No capitalizing on/insensitivity toward civil emergencies, natural disasters, public-health emergencies, conflicts, deaths, tragedies — unless clearly EDSA or awareness-raising.

## 3.6 Bullying & Harassment

- [ ] No threats, harassment, doxxing/extortion, or content designed to humiliate a person publicly.

## 3.7 Dangerous Products

- [ ] No facilitating sale of explosives, firearms, ammunition, or restricted firearm accessories (e.g., bump stocks, auto-sear conversion kits, >30-round magazines).
- [ ] No manufacturing instructions for weapons/explosives/ammunition.

## 3.8 Drugs, Alcohol, Tobacco

- [ ] **Marijuana:** No facilitating sale/delivery of marijuana or THC products, regardless of local legality.
- [ ] **Tobacco/Nicotine/Vape:** No facilitating sale; no depicting/encouraging use by minors; no implying social/health benefits; no favorable portrayal of binge drinking. Narrow exceptions exist for grocery/food-delivery apps (with age-gating) and nicotine-cessation products.
- [ ] **Unapproved substances / supplements:** No promoting/selling unapproved pharmaceuticals, supplements with dangerous ingredients (e.g., ephedra, hCG for weight loss), or products with misleading health claims. Reference list: [legitscript.com](http://www.legitscript.com).
- [ ] **Prescription drugs:** No facilitating sale without a valid prescription.
- [ ] Source: [Dangerous Products, Marijuana, Tobacco and Alcohol, Unapproved Substances](https://support.google.com/googleplay/android-developer/answer/9878810)

## 3.9 Illegal Activities

- [ ] No facilitating the sale/purchase of illegal drugs, or providing manufacturing/growing instructions.

## 3.10 Real-Money Gambling, Games & Contests

- [ ] Real-money gambling apps are only allowed with an approved application process, valid regional license, AO/Adult-Only content rating, free-to-download status (no paid app / no Play Billing for the wager itself), under-age blocking, geo-blocking outside licensed territories, and visible responsible-gambling info.
- [ ] Unlicensed real-money wagering/contests of any kind are prohibited.
- [ ] Daily Fantasy Sports apps have a separate approval process and US state-by-state licensing requirements.
- [ ] Gambling-related **ads** are allowed only under strict conditions (no targeting minors, must not simulate gambling, must not be a "companion" wagering tool, must comply with local ad-licensing law).
- [ ] Source: [Real-Money Gambling, Games, and Contests](https://support.google.com/googleplay/android-developer/answer/9877032)

## 3.11 Financial Services (Loans, Crypto, Binary Options)

- [ ] No binary options trading apps.
- [ ] **Personal loans / Earned Wage Access (EWA):** must set app category to "Finance," disclose APR/fees/repayment terms in metadata, complete a Financial Features declaration in Play Console, and (in the US) may not exceed 36% APR. Country-specific licensing required for India, Indonesia, Philippines, Nigeria, Kenya, Pakistan, Thailand.
- [ ] Loan apps **may not** request: `READ_EXTERNAL_STORAGE`, `READ_MEDIA_IMAGES/VIDEOS`, `READ_CONTACTS`, `ACCESS_FINE_LOCATION`, `READ_PHONE_NUMBERS`, `QUERY_ALL_PACKAGES`, `WRITE_EXTERNAL_STORAGE`.
- [ ] Source: [Financial Services](https://support.google.com/googleplay/android-developer/answer/17105854#financial-services)

## 3.12 Health Content & Medical Apps

- [ ] Must complete the **Health apps declaration** in Play Console.
- [ ] Must have a compliant privacy policy + prominent in-app disclosure for any health data collection.
- [ ] No misleading/harmful medical claims; must disclaim "not a medical device" unless formally regulated/approved (in which case proof of approval required).
- [ ] No health misinformation (e.g., vaccine DNA-alteration claims, advocacy of conversion therapy).
- [ ] Source: [Health Content and Services](https://support.google.com/googleplay/android-developer/answer/12261419)

## 3.13 Blockchain, Cryptocurrency & NFTs

- [ ] Crypto exchange/wallet apps must operate through certified services in regulated jurisdictions.
- [ ] No on-device cryptomining (remote-managed mining is allowed).
- [ ] Tokenized digital assets (NFTs) must be declared via the Financial Features form; no wagering NFTs for real-world-value prizes outside the gambling rules; no undisclosed "loot box"-style NFT bundles.

## 3.14 AI-Generated Content

- [ ] AI content generators (chatbots, image/video generators) must actively prevent generation of any Restricted Content (CSAM, hate speech, etc.) and Deceptive Behavior.
- [ ] Must include an in-app reporting/flagging mechanism for offensive AI outputs.
- [ ] Source: [AI-Generated Content](https://support.google.com/googleplay/android-developer/answer/16353813)

## 3.15 Intellectual Property

- [ ] No copyright infringement (unauthorized cover art, movie/TV/game assets, soundboards of copyrighted audio, unlicensed "fan art," full book reproductions).
- [ ] No trademark infringement or use of confusingly similar names/logos.
- [ ] No counterfeit goods sales/promotion.
- [ ] No apps that "encourage" infringement (e.g., unauthorized stream-ripping/downloading tools).
- [ ] Source: [Intellectual Property](https://support.google.com/googleplay/android-developer/answer/17105854#intellectual-property)

## 3.16 User-Generated Content (UGC)

If your app hosts UGC (chat, posts, comments, AR content, etc.) you must:
- [ ] Require acceptance of terms/community guidelines before posting.
- [ ] Define and prohibit objectionable content in those terms.
- [ ] Provide in-app reporting/blocking tools appropriate to the UGC type (1:1 messaging needs blocking; public UGC needs report+block for both content and users).
- [ ] "Incidental" sexual UGC is tolerated only if hidden by default behind a 2-step filter, minors are screened out, and the content-rating questionnaire is answered accurately.
- [ ] Source: [User Generated Content](https://support.google.com/googleplay/android-developer/answer/17105854#user-generated-content)
---

## 4. Privacy, Data & Permissions — What You Must Disclose and Do

Source: [Privacy, Deception and Device Abuse](https://support.google.com/googleplay/android-developer/topic/9877467)

## 4.1 Privacy Policy (mandatory for every app)

- [ ] Must be posted at a public, non-geofenced, non-PDF, **editable-only-by-you** URL, linked in Play Console **and** inside the app.
- [ ] Must name the developer/entity that matches the store listing.
- [ ] Must disclose: what data is collected, how it's used/shared, security practices, retention/deletion policy, and a contact method.
- [ ] Source: [User Data policy](https://support.google.com/googleplay/android-developer/answer/10144311)

## 4.2 Data Safety Section (Play Console form)

- [ ] Must be completed accurately for **every** app, kept in sync with the privacy policy.
- [ ] Source: [Data safety section](https://support.google.com/googleplay/android-developer/answer/10787469)

## 4.3 Prominent Disclosure & Consent

Required whenever data collection would **not** be within a user's reasonable expectation (e.g., background location, screen recording, contact-list access):
- [ ] In-app disclosure (not just in a privacy policy) explaining what's collected and why.
- [ ] Explicit affirmative consent (tap to accept) **before** collection begins — no auto-dismiss, no "navigating away = consent."
- [ ] Suggested phrasing: *"[App] collects [data] to enable [feature], [scenario]."*

## 4.4 Selling Data — Prohibited

- [ ] Personal/sensitive user data may never be **sold** to third parties. User-initiated transfers (e.g., explicit file-sharing) are not considered a "sale."

## 4.5 Account Deletion

- [ ] If your app supports account creation, it must let users **delete** the account (not just deactivate) both **in-app** and via an external web URL entered in Play Console.
- [ ] Source: [Account Deletion Requirement](https://support.google.com/googleplay/android-developer/answer/13327111)

## 4.6 Restricted/Sensitive Permissions — Key Rules

| Permission Group | Rule |
|---|---|
| Photos/Video (`READ_MEDIA_IMAGES/VIDEO`) | Only if system pickers (Android Photo Picker) are insufficient; declaration required if targeting API 33+ |
| SMS / Call Log | Only allowed if app is the **default** SMS/Phone/Assistant handler; must stop use immediately if handler status changes |
| Location (fine/coarse/background) | Minimum necessary scope only; never for ads/analytics alone; background use must be core-feature-justified |
| All Files Access (`MANAGE_EXTERNAL_STORAGE`) | Only for apps whose core function needs broad storage access; must pass access review |
| Package Visibility (`QUERY_ALL_PACKAGES`) | Only for apps needing awareness of/interoperability with any installed app; scoped alternatives preferred |
| Accessibility API | Cannot be used to change settings without consent, bypass security, enable remote call recording, or power autonomous agents; must be documented in listing |
| `REQUEST_INSTALL_PACKAGES` | Only for core functionality like browsers, file managers, device migration, EMM — never for self-updating outside Play |
| Body Sensors / Health Connect | Must map to fitness, medical monitoring, or research use cases; data may never be sold or used for insurance/employment/ad purposes |
| VPN Service | Core-functionality VPN apps only; must encrypt traffic and disclose in listing; cannot redirect traffic for ad monetization |
| Exact Alarm (`USE_EXACT_ALARM`) | Only alarm/timer/calendar-notification apps |
| Full-Screen Intent | Automatically granted only for alarm/incoming-call apps (API 34+); otherwise must request |
| Age Signals API | Data may be used **only** for legal age-compliance purposes — never ads, profiling, or third-party sale |
| Android Advertising ID (AAID) / App Set ID | AAID only for ads/analytics, must respect opt-out; App Set ID must never be used for ad personalization |
Full source: [Permissions and APIs that Access Sensitive Information](https://support.google.com/googleplay/android-developer/answer/16558241)

## 4.7 Device & Network Abuse (Prohibited)

Google Play prohibits an app (or any SDK inside it) from interfering with, disrupting, damaging, or gaining unauthorized access to a user's device, other devices, networks, APIs, services, other apps, any Google service, or a carrier's network. Specifically:
- [ ] **No self-updating outside Google Play.** An app may not modify, replace, or update itself using any method other than Google Play's own update mechanism.
- [ ] **No external/dynamic executable code.** An app (or third-party SDK inside it) may **not download executable code** — dex files, JAR files, native `.so` libraries — from any source other than Google Play. *(Exception: code that runs inside a VM/interpreter that only gets indirect API access, e.g., JavaScript in a WebView/browser, is allowed.)*
- [ ] **Interpreted/scripting languages loaded at runtime** (JavaScript, Python, Lua, etc., not packaged with the app) are allowed **only if** they cannot be used to violate other Play policies — i.e., you can't use a runtime script engine as a loophole to smuggle in disallowed behavior.
- [ ] **No introducing or exploiting security vulnerabilities.**
- [ ] **Third-party API Terms of Service must be respected.** An app may not access or use any service/API in a way that violates that service's own ToS. *Google's own explicit example: your app must not download, monetize, or play back YouTube videos in a way that violates the YouTube API Services Terms of Service* (e.g., stripping ads, bypassing playback restrictions, or building a YouTube-video player/aggregator that functions as a YouTube clone). This is a common real-world rejection reason for apps that embed or play YouTube content.
- [ ] **No installing other apps without explicit prior user consent**, and no linking to / facilitating the distribution or installation of malicious software (see also §5.4 Hostile Downloaders below, which covers linking to/bundling non-malicious but unwanted APKs).
- [ ] No blocking/interfering with another app's ads; no game-cheating tools that affect other apps' gameplay; no providing hacking instructions or circumventing security protections; no bypassing system power management (Doze/App Standby) unless properly allowlisted; no facilitating third-party proxy services unless that is the app's primary, disclosed, user-facing purpose.
- [ ] No use of WebViews with an added JavaScript Interface that load untrusted web content (plain `http://` URLs) or unverified URLs from untrusted Intents.
- [ ] No circumventing the Android app sandbox to derive another app's user activity or identity.
**Related sub-policies bundled under Device & Network Abuse:**
- [ ] **Foreground Service (FGS) permissions** — for apps targeting Android 14+, each foreground service must declare a valid, justified FGS type (user-initiated/perceptible, stoppable by the user, can't be deferred by the system, runs only as long as needed). [Foreground Services policy](https://support.google.com/googleplay/android-developer/answer/9888379)
- [ ] **User-Initiated Data Transfer Jobs** — this API may only be used for user-initiated network transfers that run only as long as needed; no automatic/background-initiated transfers.
- [ ] **FLAG_SECURE** — all apps must respect other apps' `FLAG_SECURE` declaration (which blocks screenshots/screen-sharing of sensitive UI) and must not create workarounds to bypass it, even for Accessibility Tools.
- [ ] **On-device Android Containers** — apps that run their own simulated Android environment (app-cloning/multi-instance/virtualization apps) must respect another app's `REQUIRE_SECURE_ENV` manifest flag and refuse to load it, with no proxying or bypass workarounds.
Source: [Device and Network Abuse — full policy](https://support.google.com/googleplay/android-developer/answer/16559646)
---

## 4.8 Mobile Unwanted Software (MUwS) Family — Device-Abuse-Adjacent Policies

This is a distinct policy family (separate top-level section in the Play Policy Center) that covers behavior that harms device usability/trust even when it isn't classic "malware." It has four parts:
**a) Mobile Unwanted Software (core policy)**
- [ ] App must deliver on its promised value and disclose all bundled software/system changes, with user review/approval of significant changes.
- [ ] Must not misrepresent device state (e.g., fake "your device is infected" warnings).
- [ ] Must not request or trick users into disabling **Google Play Protect** or other device security protections (e.g., no "unlock this feature if you disable Play Protect" bargains).
- [ ] Must not interfere with other apps or normal device usability; ads must be dismissible and not mimic system prompts; uninstall must be straightforward.
- [ ] Source: [Mobile Unwanted Software](https://support.google.com/googleplay/android-developer/answer/9970222)
**b) Hostile Downloaders — directly covers "app installs/links to other app APKs"**
- [ ] An app is a **hostile downloader** (banned) if it's believed to be designed to spread Mobile Unwanted Software, **or** if ≥5% of the apps it downloads/installs turn out to be MUwS (minimum sample: 500 downloads / 25 MUwS).
- [ ] **Practical rule for you:** an app generally should not link to, bundle, or auto-install other apps' APKs. The only exception is a **major browser or legitimate file-sharing app** — and even then, every download must be **explicitly initiated by the consenting user**, never driven automatically or silently.
- [ ] Source: [Hostile Downloaders](https://support.google.com/googleplay/android-developer/answer/11189134)
**c) Ad Fraud**
- [ ] Banned: invisible/hidden ads, auto-clicking ads or bot-generated ad traffic, faking install-attribution clicks, showing ads outside the app (pop-ups when the user isn't in-app), and misrepresenting the app's identity to ad networks (fake package name, fake OS/device type).
- [ ] Source: [Ad Fraud](https://support.google.com/googleplay/android-developer/answer/9969955)
**d) Unauthorized Use or Imitation of System Functionality**
- [ ] System-level notifications may only be used for an app's own integral features (e.g., an airline app's deal alert, a game's promo alert).
- [ ] Banned: using a system notification/alert slot to serve an ad, or any notification/alert designed to look like it comes from the Android OS itself.
- [ ] Source: [Unauthorized Use or Imitation of System Functionality](https://support.google.com/googleplay/android-developer/answer/9969861)
**e) Social Engineering** *(formerly named "Impersonation" inside this policy family before a 2020 rename — not to be confused with the separate standalone Impersonation policy in §5.2, which covers brand/identity impersonation specifically)*
- [ ] Covers broader manipulation tactics: apps must not mislead users into taking security-relevant actions (e.g., fake "your battery is critical" or fake "update required" prompts designed to get a tap/install) or otherwise psychologically manipulate users into unsafe actions.
- [ ] Source: [Social Engineering](https://support.google.com/googleplay/android-developer/answer/10148323)
---

## 5. Deception, Impersonation & Malware — What Gets Apps Removed


## 5.1 Deceptive Behavior

Five sub-areas, all prohibited:
1. **Misleading Claims** — false functionality claims, impossible features ("breathalyzer via phone"), fake "Official" titles, election misinformation, false government affiliation.
2. **Deceptive Device Settings Changes** — changing browser/homescreen/system settings without clear, reversible, explicit consent; never as a monetization/third-party service.
3. **Enabling Dishonest Behavior** — no generating fake IDs/passports/SSNs/credit cards; no undisclosed geography/device-based functionality differences; no obfuscating behavior to evade review; must disclose download size before fetching extra assets.
4. **Manipulated Media** — no deepfakes/misleading media about sensitive events, politics, or public figures without clear watermark/disclaimer; exceptions for obvious satire/parody.
5. **Behavior Transparency** — no hidden/dormant/remotely-activated features; app must behave identically for reviewers and real users; no techniques to detect/evade review.
Source: [Deceptive Behavior](https://support.google.com/googleplay/android-developer/answer/13610059)

## 5.2 Impersonation

- [ ] No implying false affiliation with another company/developer/government.
- [ ] No icons/titles confusingly similar to existing products, brands, or public figures ("Justin Bieber Official" without rights).
- [ ] Must follow [Android Brand Guidelines](https://developer.android.com/distribute/tools/promote/brand.html).
- [ ] Source: [Impersonation](https://support.google.com/googleplay/android-developer/answer/9888374)

## 5.3 Malware — Zero Tolerance

- [ ] Any code that puts a user, their data, or their device at risk is banned outright: trojans, phishing, spyware, backdoors, "riskware" (formerly "maskware" — apps that behave differently to evade detection).
- [ ] Must remove any third-party SDK later found to distribute malicious code.
- [ ] Source: [Malware](https://support.google.com/googleplay/android-developer/answer/9888380)
---

## 6. Monetization & Ads


## 6.1 Payments / Billing Rules

- [ ] Digital goods, subscriptions, in-app currencies, and app-functionality unlocks **must** use Google Play's billing system (limited exceptions: physical goods/services, peer-to-peer payments, tax-exempt donations, certain gambling-policy-compliant products, alternative billing programs in eligible regions).
- [ ] No leading users to external payment methods via listing, in-app webviews, buttons, or sign-up flows (unless enrolled in an eligible alternative-billing program).
- [ ] Loot boxes/randomized purchase mechanics must disclose odds before purchase.
- [ ] Source: [Payments](https://support.google.com/googleplay/android-developer/answer/9858738)

## 6.2 Ads Rules

- [ ] Ads/offers must match the app's own content rating (no Mature ads in an Everyone-rated app).
- [ ] **No deceptive ads:** must not mimic system UI, OS warnings, or notifications.
- [ ] **No disruptive ads:** must not force clicks, must be dismissible, must not appear outside the serving app or on the home screen, must not trigger via home/back button.
- [ ] **Better Ads compliance:** no full-screen interstitials at unexpected moments (level starts, app launch splash) and interstitials must be closeable within 15 seconds (exception: opted-in rewarded ads).
- [ ] No lockscreen ad monetization unless the app *is* a lockscreen replacement.
- [ ] Location data may not be collected solely for ad targeting; must comply with prominent disclosure rules.
- [ ] Ad Fraud is strictly prohibited.
- [ ] Source: [Ads](https://support.google.com/googleplay/android-developer/answer/9857753)

## 6.3 Subscriptions

- [ ] Terms and pricing must be clear and match the Play billing interface exactly.
- [ ] Source: [Subscriptions](https://support.google.com/googleplay/android-developer/answer/9900533)
---

## 7. Store Listing & Promotion


## 7.1 Metadata (Title, Icon, Description, Screenshots)

- [ ] **Title ≤ 30 characters.**
- [ ] No emojis, emoticons, repeated special characters, or ALL CAPS (unless part of registered brand).
- [ ] No text/images implying ranking ("#1," "Best of Play"), pricing/promotions ("10% off," "Free for a limited time"), or false Play-program affiliation ("Editor's Choice," "New").
- [ ] No misleading icon symbols (fake notification dots, fake download/install icons).
- [ ] No unattributed/anonymous testimonials, comparison callouts, or keyword-stuffed word lists.
- [ ] No sexually suggestive imagery, profanity, graphic violence, or illicit-drug depictions in listing assets — even EDSA content must be listing-safe.
- [ ] Source: [Metadata](https://support.google.com/googleplay/android-developer/answer/9898842)

## 7.2 Content Ratings (IARC)

- [ ] Every app must have a completed, accurate IARC content-rating questionnaire; apps without a rating are disallowed.
- [ ] Ads shown in-app must not be significantly more mature than the app's own rating.
- [ ] Ratings can vary by territory; "Refused Classification" in a territory results in regional removal.
- [ ] Source: [Content Ratings](https://support.google.com/googleplay/android-developer/answer/9898843) · [Ratings questionnaire](https://support.google.com/googleplay/android-developer/answer/9859655)
---

## 8. Spam & Minimum Functionality

- [ ] App must provide a **stable, responsive, engaging** experience — no crashes, freezes, or force-closes.
- [ ] Not allowed: static/no-functionality apps (text-only, single-wallpaper, PDF-viewer-only apps with no original value), apps that don't install/load, apps that load but are unresponsive.
- [ ] **Repetitive content**: no spam, unsolicited messaging, or near-duplicate apps published to game search/rankings.
- [ ] Source: [Functionality, Content, and User Experience](https://support.google.com/googleplay/android-developer/answer/9898783) · [Spam](https://support.google.com/googleplay/android-developer/answer/9899034)
---

## 9. Families Policy (Apps Targeting or Attracting Children)

Required **only if** any target-audience age group includes children — but read this even if you're unsure, since Google independently assesses whether your imagery/content "targets" children regardless of your declared audience.
Key requirements if children are a target audience:
1. **App content** must be age-appropriate.
2. **App functionality** cannot be a bare webview/affiliate-traffic wrapper.
3. **Play Console answers** (Target Audience, Data safety, IARC questionnaire) must be accurate and kept current.
4. **Data practices**:
- [ ] No transmitting AAID, SIM/Build serials, BSSID, MAC, SSID, IMEI/IMSI from child users.
- [ ] No requesting device phone number via `TelephonyManager`.
- [ ] No precise location collection from apps solely targeting children.
- [ ] Bluetooth must use Companion Device Manager where available.
5. **APIs/SDKs** used must be approved for child-directed services (or gated behind a neutral age screen for mixed-audience apps).
6. **AR features** require a safety warning (parental supervision reminder, physical-hazard awareness) and must avoid devices unsuitable for children.
7. **Social apps/features**: must include online-safety reminders and require "adult action" (PIN/password/ID/etc.) before enabling personal-info exchange for child users. Apps whose main purpose is chatting with strangers (dating apps, chat-roulette, open kids' chat rooms) **must not target children** at all.
8. **Legal compliance**: COPPA (US), GDPR (EU), and other applicable child-privacy laws.
**Ads to children** must:
- [ ] Come only from **Google Play Families Self-Certified Ads SDKs**.
- [ ] Never be interest-based/remarketing.
- [ ] Be content-appropriate, non-disruptive (dismissible within 5 seconds), never full-screen-on-launch, never more than one ad placement per page, and never deceptive/manipulative.
- [ ] Never advertise: mature media, mature games/software, alcohol/tobacco/drugs, gambling (even simulated), sexual/dating content, or graphic violence.
Source: [Google Play Families Policies](https://support.google.com/googleplay/android-developer/answer/9893335) · [Families Self-Certified Ads SDK list](https://support.google.com/googleplay/android-developer/answer/9283445)
---

## 10. Age-Restricted Content & "Restrict Minor Access"

Some app categories are **required** to block minors using the Play Console's **Restrict Minor Access** feature:
1. Real-Money Gambling, Games & Contests apps.
2. Apps whose **core function** is matchmaking/dating (apps where dating is only an *incidental* feature can instead use effective in-app age-gating rather than the console-level block).
Additionally, Google is separately rolling out **AI-based age-estimation** across its ecosystem (Search, YouTube, Play) that can restrict a Google Account's access to mature/adult-only apps regardless of the developer's own settings — driven partly by new US state laws (Texas, Utah, Louisiana, and upcoming California in 2027).
Source: [Age-Restricted Content and Functionality](https://support.google.com/googleplay/android-developer/answer/16838200) · [Manage target audience / Restrict Minor Access](https://support.google.com/googleplay/android-developer/answer/9867159)
---

## 11. Account, Technical & Publishing Requirements


## 11.1 Developer Account Setup

- [ ] **Personal account** ($25 one-time fee): for individuals/hobbyists; requires government-ID identity verification if no verified Google Payments profile exists.
- [ ] **Organization account**: required for businesses, and *mandatory* for apps in Financial Services, Health, VPN, and Government categories. Requires a **D-U-N-S number** (Dun & Bradstreet business identifier — free, but can take up to 30 days to obtain) plus organization documentation.
- [ ] Source: [Choose account type](https://support.google.com/googleplay/android-developer/answer/13634885) · [Verify developer identity](https://support.google.com/googleplay/android-developer/answer/10841920) · [D-U-N-S info](https://support.google.com/googleplay/android-developer/answer/13628312)

## 11.2 Android Developer Verification (separate, newer, platform-wide program)

- [ ] Distinct from Play Console account verification. Verifies your **identity** *and* registers the **package name + signing key** of every app so it can be installed on **certified Android devices**, including outside Google Play.
- [ ] Enforcement begins **September 2026** in select regions/stores.
- [ ] A free "limited distribution" tier exists for students/hobbyists (apps capped at 20 explicitly-authorized devices).
- [ ] Source: [developer.android.com/developer-verification](https://developer.android.com/developer-verification)

## 11.3 Closed Testing Requirement (New Personal Accounts)

- [ ] Applies to **personal** accounts created **after Nov 13, 2023** (organization accounts exempt).
- [ ] Must run a closed test with a minimum of **12 opted-in testers** for **14 continuous days** before production access is granted (reduced from 20 testers in Dec 2024).
- [ ] "Opted-in" = tester accepted invite and installed under matching Google account; must remain installed and used throughout the 14 days.
- [ ] Source: [App testing requirements for new personal developer accounts](https://support.google.com/googleplay/android-developer/answer/14151465)

## 11.4 Build & Technical Requirements

- [ ] **Format:** Android App Bundle (.aab) — plain APKs no longer accepted for new app submissions. Use **Play App Signing**.
- [ ] **Target API level:** must target within 1 year of the latest major Android release for new submissions/updates (as of mid-2026: API 35 minimum, **API 36 required from Aug 31, 2026**; Wear/TV/Automotive floor is one level lower). Existing unupdated apps become invisible to new users on newer OS versions if they fall more than 2 years behind.
- [ ] **64-bit architecture:** required for any app shipping native (NDK) code.
- [ ] **App icon:** 512×512 px, 32-bit PNG with alpha, ≤1MB. **Feature graphic:** 1024×500 px. Minimum 2 phone screenshots (up to 8 per device type).
- [ ] Source: [Target API Level Policy](https://support.google.com/googleplay/android-developer/answer/16561298) · [App Bundle](https://developer.android.com/guide/app-bundle) · [64-bit](https://developer.android.com/google/play/requirements/64-bit)
---

## 12. Enforcement & Appeals

- [ ] Violations can result in: app rejection, app removal/suspension, or full developer account termination.
- [ ] As of late 2025, Google introduced a **180-day appeal window** for account terminations.
- [ ] Non-compliant apps typically get **at least 30 days** notice to fix an issue before removal (varies by policy/severity — CSAM and malware are immediate).
- [ ] Appeals and help: https://play.google.com/console/developers/help-and-support
- [ ] Community troubleshooting: https://support.google.com/googleplay/android-developer/community
---

## 13. Recency Notes / Things That Changed Recently (as of July 2026)

- [ ] **Android Developer Verification** is a brand-new, platform-wide (not just Play-specific) identity + app-registration requirement starting enforcement Sept 2026 — distinct from the older Play Console D-U-N-S/ID verification.
- [ ] **Age-Restricted Content** policy was clarified (April 2026) so dating features that are merely *incidental* to an app no longer require full Restrict Minor Access — alternative age-gating suffices.
- [ ] **Health & Fitness data guidance** was expanded (April 2026) for new Android 16 Health Connect granular permissions (Menstrual Cycle, Alcohol Consumption, Symptoms) with an explicit ban on using this data for employment/insurance decisions or unauthorized social sharing.
- [ ] **Financial Features declaration** is now mandatory for every app on an account, even apps with zero financial features — incomplete declarations block all app updates.
- [ ] **Closed testing** requirement dropped from 20 → 12 testers (Dec 2024), still in force for personal accounts.
- [ ] **Malware category renamed:** "Maskware" → "Riskware" (Oct 2025) for industry-terminology alignment.
- [ ] **Target API level** climbing to API 36 (Android 16) mandatory Aug 31, 2026.
---

## Appendix: Full List of Policy Sub-Pages Referenced

| Topic | URL |
|---|---|
| Full Developer Program Policy | https://support.google.com/googleplay/android-developer/answer/17105854 |
| Child Safety Standards | https://support.google.com/googleplay/android-developer/answer/14747720 |
| User Data | https://support.google.com/googleplay/android-developer/answer/10144311 |
| Permissions & Sensitive APIs | https://support.google.com/googleplay/android-developer/answer/16558241 |
| Device and Network Abuse | https://support.google.com/googleplay/android-developer/answer/16559646 |
| Foreground Service (FGS) Permissions | https://support.google.com/googleplay/android-developer/answer/9888379 |
| Mobile Unwanted Software (core) | https://support.google.com/googleplay/android-developer/answer/9970222 |
| Hostile Downloaders | https://support.google.com/googleplay/android-developer/answer/11189134 |
| Ad Fraud | https://support.google.com/googleplay/android-developer/answer/9969955 |
| Unauthorized Use or Imitation of System Functionality | https://support.google.com/googleplay/android-developer/answer/9969861 |
| Social Engineering | https://support.google.com/googleplay/android-developer/answer/10148323 |
| YouTube API Services Developer Policies | https://developers.google.com/youtube/terms/developer-policies-guide |
| Deceptive Behavior | https://support.google.com/googleplay/android-developer/answer/13610059 |
| Impersonation | https://support.google.com/googleplay/android-developer/answer/9888374 |
| Malware | https://support.google.com/googleplay/android-developer/answer/9888380 |
| Google Play's Target API Level Policy | https://support.google.com/googleplay/android-developer/answer/16561298 |
| Payments | https://support.google.com/googleplay/android-developer/answer/9858738 |
| Ads | https://support.google.com/googleplay/android-developer/answer/9857753 |
| Subscriptions | https://support.google.com/googleplay/android-developer/answer/9900533 |
| Metadata | https://support.google.com/googleplay/android-developer/answer/9898842 |
| Content Ratings | https://support.google.com/googleplay/android-developer/answer/9898843 |
| Functionality, Content & User Experience | https://support.google.com/googleplay/android-developer/answer/9898783 |
| Spam / Repetitive Content | https://support.google.com/googleplay/android-developer/answer/9899034 |
| Google Play Families Policies | https://support.google.com/googleplay/android-developer/answer/9893335 |
| Real-Money Gambling, Games, Contests | https://support.google.com/googleplay/android-developer/answer/9877032 |
| Account Deletion Requirement | https://support.google.com/googleplay/android-developer/answer/13327111 |
| Data Safety Section | https://support.google.com/googleplay/android-developer/answer/10787469 |
| Choose a Developer Account Type | https://support.google.com/googleplay/android-developer/answer/13634885 |
| Verify Developer Identity | https://support.google.com/googleplay/android-developer/answer/10841920 |
| D-U-N-S Number Info | https://support.google.com/googleplay/android-developer/answer/13628312 |
| App Testing Requirements (12 testers/14 days) | https://support.google.com/googleplay/android-developer/answer/14151465 |
| Android Developer Verification | https://developer.android.com/developer-verification |
| Android App Bundle | https://developer.android.com/guide/app-bundle |
| 64-bit Architecture Requirement | https://developer.android.com/google/play/requirements/64-bit |
| Developer Distribution Agreement | https://play.google.com/about/developer-distribution-agreement.html |
| Public Developer Content Policy (overview) | https://play.google/developer-content-policy/ |
| Policy Announcements Changelog | https://support.google.com/googleplay/android-developer/announcements/13412212 |
---
*This document is a research compilation for internal validation purposes and is not a substitute for reading Google's live policy pages or obtaining legal advice. Policies are updated frequently — re-verify against the linked sources before final app submission.*