# Patchs Leveragers sur Element X Android

« Agents by Leveragers » : la branche `leveragers` part de l'étiquette officielle Element `v26.11.0`
(`5d1af53`, 06/11/2026), avec ces patchs par-dessus. Ce fichier sert à celui qui les rejoue sur une nouvelle
étiquette (rebase) : une ligne par patch, avec les fichiers d'Element qu'il touche. Règle : une étiquette publiée
depuis au moins 14 jours. L'app iOS jumelle est `Leveragers-AI/element-x-ios` (mêmes choix).

| Patch | But | Fichiers d'Element touchés |
|---|---|---|
| Identité | Identifiant `com.leveragers.agents`, nom « Agents » ; retour de connexion `com.leveragers.agents:/` ; `client_uri` `https://leveragers.com`, logo sur agents.leveragers.com, conditions et confidentialité sur www.leveragers.com, copyright = ce dépôt ; Firebase retiré (pas de push pour l'instant ; UnifiedPush reste compilé, inactif sans distributeur) ; statistiques PostHog/Sentry remplacées par le module no-op ; « Agents » à la place d'« Element » pour l'app de bureau et l'app mobile | `plugins/src/main/kotlin/config/BuildTimeConfig.kt`, `plugins/src/main/kotlin/ModulesConfig.kt`, `appconfig/.../ApplicationConfig.kt` |
| Serveur et fonctions | `chat.leveragers.com` seul (un bouton sur l'écran d'accueil, ni « Créer un compte » ni QR), rapports de bug coupés, appels masqués, salons publics refusés, entrée « Parcourir les salons » retirée ; tests des modules touchés alignés | `features/enterprise/impl-foss/.../Default{,Session}EnterpriseService.kt` (+ tests), `appconfig/.../OnBoardingConfig.kt`, `features/login/impl/.../onboarding/OnBoardingPresenter.kt` (+ test), `features/startchat/impl/.../root/StartChatView.kt` (+ test) |
| Marque | Icône adaptative (fond cyan `#2CF8E5`, marque bleu nuit `#0A082B`, monochrome), icônes PNG Android 7 et Play Store ; logo de l'écran d'accueil (`onboarding_logo`, 110 dp) sur fond bleu nuit, apparence sombre ; icône et couleur des notifications | `appicon/element/src/**`, `app/src/main/res/{drawable/onboarding_logo.xml,raw/leveragers_keep.xml}`, `features/login/impl/.../onboarding/OnBoardingView.kt`, `libraries/designsystem/src/main/res/drawable/ic_notification.xml`, `appconfig/.../NotificationConfig.kt` |
| Construction | Workflow unique `leveragers-android.yml` (APK signés + connexion sur émulateur avec un compte jetable, Maestro) ; workflows d'Element retirés de la branche ; signature de la version `release` par notre clé quand l'environnement la fournit | `.github/workflows/*`, `app/build.gradle.kts`, `.maestro-leveragers/*` |

Restent, comme la politique de marque d'Element l'autorise : les mentions des vrais produits Element (par exemple
« Ask if the caller can use the new Element X app » pour un ancien appel) et les mentions de droits d'auteur et de
licence. Le code est sous AGPL-3.0 (voir `LICENSE`) : ce dépôt public contient la source exacte de chaque version
distribuée.

## Connexion

Element X Android ouvre la page de connexion du serveur dans un onglet Chrome (Custom Tab) et revient dans l'app par
le schéma privé `com.leveragers.agents:/`. Côté serveur (Tuwunel 2.0.0), ce schéma doit figurer dans
`oidc_registration_allowed_redirect_hosts` (ajouté sur chat.leveragers.com le 23/11/2026) et commencer par l'hôte du
`client_uri` à l'envers (`com.leveragers…` pour `https://leveragers.com`) ; `logo_uri`, `tos_uri`, `policy_uri`
doivent être sur cet hôte ou un sous-domaine.

## Construction

`.github/workflows/leveragers-android.yml`, lancé à la main (`workflow_dispatch`) : tests unitaires des modules
touchés, `assembleGplayRelease` (APK par architecture + APK universel), signature par notre clé, sommes SHA-256 ;
puis, sur demande, connexion à chat.leveragers.com sur un émulateur (API 33) avec le compte jetable du secret
`TEST_ACCOUNT_*`. Seules les captures d'écran sont publiées (le journal de Maestro contient le texte saisi).
Secrets du dépôt : `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`,
`TEST_ACCOUNT_EMAIL`, `TEST_ACCOUNT_PASSWORD`. La clé (PKCS#12) vient de hub2, où elle est gardée et sauvegardée.
Numéros de version : ceux d'Element pour l'instant (à revoir avant une deuxième version sur la même étiquette).

## Pas encore fait

- Push (Firebase à nous + passerelle Sygnal commune avec iOS).
- Filtres de liens d'Element restés dans le manifeste (`app.element.io`, `mobile.element.io`, `element://`,
  `elementx://`) : sans vérification possible pour notre identifiant, Android peut proposer un choix d'app.
- Permissions liées aux fonctions coupées (appels plein écran, installation d'APK, position) : à retirer avant le
  Play Store, pour éviter les déclarations correspondantes.
