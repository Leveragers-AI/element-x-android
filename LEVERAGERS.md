# Patchs Leveragers sur Element X Android

« Agents by Leveragers » : la branche `leveragers` part de l'étiquette officielle Element `v26.11.0`
(`5d1af53`, 06/11/2026), avec ces patchs par-dessus. Ce fichier sert à celui qui les rejoue sur une nouvelle
étiquette (rebase) : une ligne par patch, avec les fichiers d'Element qu'il touche. Règle : une étiquette publiée
depuis au moins 14 jours. L'app iOS jumelle est `Leveragers-AI/element-x-ios` (mêmes choix).

Les fonctions d'Element masquées le sont par des interrupteurs de `appconfig/.../LeveragersConfig.kt` plutôt que par
des suppressions, comme `AppSettings` sur iOS : le code reste en place et les rebases restent simples.

| Patch | But | Fichiers d'Element touchés |
|---|---|---|
| Identité | Identifiant `com.leveragers.agents`, nom « Agents » ; retour de connexion `com.leveragers.agents:/` ; `client_uri` `https://leveragers.com`, logo sur agents.leveragers.com, conditions et confidentialité sur www.leveragers.com, copyright = ce dépôt ; Firebase retiré (pas de push pour l'instant ; UnifiedPush reste compilé, inactif sans distributeur) ; statistiques PostHog/Sentry remplacées par le module no-op ; « Agents » à la place d'« Element » pour l'app de bureau et l'app mobile | `plugins/src/main/kotlin/config/BuildTimeConfig.kt`, `plugins/src/main/kotlin/ModulesConfig.kt`, `appconfig/.../ApplicationConfig.kt` |
| Serveur et fonctions | `chat.leveragers.com` seul (un bouton sur l'écran d'accueil, ni « Créer un compte » ni QR), rapports de bug coupés, appels masqués, salons publics refusés, entrée « Parcourir les salons » retirée ; tests des modules touchés alignés | `features/enterprise/impl-foss/.../Default{,Session}EnterpriseService.kt` (+ tests), `appconfig/.../OnBoardingConfig.kt`, `features/login/impl/.../onboarding/OnBoardingPresenter.kt` (+ test), `features/startchat/impl/.../root/StartChatView.kt` (+ test) |
| Push absent | Pas de fenêtre d'erreur « No distributors available » / « No providers available » à chaque connexion : sans push dans cette version, c'est l'état attendu | `appnav/.../loggedin/LoggedInView.kt` |
| Marque | Icône adaptative (fond cyan `#2CF8E5`, marque bleu nuit `#0A082B`, monochrome), icônes PNG Android 7 et Play Store ; logo de l'écran d'accueil (`onboarding_logo`, 110 dp) sur fond bleu nuit, apparence sombre ; icône et couleur des notifications | `appicon/element/src/**`, `app/src/main/res/{drawable/onboarding_logo.xml,raw/leveragers_keep.xml}`, `features/login/impl/.../onboarding/OnBoardingView.kt`, `libraries/designsystem/src/main/res/drawable/ic_notification.xml`, `appconfig/.../NotificationConfig.kt` |
| Construction | Workflows `leveragers-android.yml` (APK signés + connexion sur émulateur avec un compte jetable, Maestro) et `leveragers-emulator.yml` (connexion sur émulateur sans reconstruire, ou témoin avec l'Element X officiel) ; workflows d'Element retirés de la branche ; signature de la version `release` par notre clé quand l'environnement la fournit | `.github/workflows/*`, `app/build.gradle.kts`, `.maestro-leveragers/*` |
| Écran « Démarrer une discussion » | « Inviter des personnes sur Agents » retiré (comptes créés par Leveragers) et « Rejoindre un salon par son adresse » retiré (salons rejoints sur invitation ou par leur espace) ; interrupteurs `LeveragersConfig.CAN_INVITE_FRIENDS_TO_APP`, `CAN_JOIN_ROOM_BY_ADDRESS` | `appconfig/.../LeveragersConfig.kt` (nouveau), `features/startchat/impl/build.gradle.kts`, `features/startchat/impl/.../root/StartChatView.kt` (+ test) |
| Sondages | Plus de création de sondage (menu de la barre de rédaction) ni d'historique des sondages (détails du salon), comme sur notre Element Web ; un sondage reçu reste affiché et on peut y voter ; interrupteur `LeveragersConfig.CAN_CREATE_POLLS` | `features/messages/impl/.../messagecomposer/AttachmentsBottomSheet.kt`, `features/roomdetails/impl/.../RoomDetailsView.kt` (+ tests des deux vues) |
| Blocage et signalement | Ni blocage ni signalement (même organisation) : « Signaler » du menu d'un message, « Signaler le salon » (menu d'un salon de l'accueil, détails du salon), « Refuser et bloquer » d'une invitation (accueil, écran d'invitation), « Bloquer » d'une conversation privée et de la fiche d'un membre, utilisateurs bloqués des réglages ; interrupteur `LeveragersConfig.ALLOWS_BLOCKING_AND_REPORTING` | `features/messages/impl/.../actionlist/ActionListPresenter.kt`, `features/home/impl/.../roomlist/{RoomListPresenter,RoomListDeclineInviteMenu}.kt`, `features/joinroom/impl/.../JoinRoomView.kt`, `features/roomdetails/impl/.../RoomDetailsView.kt`, `features/userprofile/shared/.../UserProfileView.kt`, `features/preferences/impl/.../moderation/ModerationAndSafetyView.kt`, `build.gradle.kts` de joinroom, userprofile/shared, preferences (+ tests) |
| Inviter depuis un message privé | « Inviter des personnes » retiré des détails d'une conversation privée (elle deviendrait un groupe avec l'agent ; le Web ne le propose pas) ; les salons gardent « Inviter », selon les niveaux de pouvoir ; interrupteur `LeveragersConfig.ALLOWS_INVITING_FROM_DIRECT_MESSAGES` | `features/roomdetails/impl/.../RoomDetailsView.kt` (+ test) |
| Partage des salons | « Partager » retiré des détails d'un salon et du menu d'un espace (nos salons ne s'ouvrent que depuis nos apps) ; interrupteur `LeveragersConfig.ALLOWS_SHARING_ROOMS` | `features/roomdetails/impl/.../RoomDetailsView.kt` (+ test), `features/space/impl/.../root/SpaceView.kt`, `features/space/impl/build.gradle.kts` |
| Légendes des médias | Plus d'avertissement « les légendes peuvent ne pas s'afficher dans d'anciennes apps » (nos apps web et mobiles les affichent) ; interrupteur `LeveragersConfig.SHOWS_MEDIA_CAPTION_WARNING` | `libraries/textcomposer/impl/.../model/MessageComposerMode.kt`, `libraries/textcomposer/impl/build.gradle.kts` |
| Réglages simplifiés, sans position | Réglages sans statut sous le profil, ni Labs, ni options développeur (plus actives hors `release`, ni débloquées par sept tapes sur la version) : `LeveragersConfig.HAS_SIMPLIFIED_SETTINGS`. Pas de partage de position (menu de la barre de rédaction, réglage « Partage de position ») : `CAN_SHARE_LOCATION` ; permissions de position retirées du manifeste, Android refuse tout accès | `features/preferences/impl/.../root/PreferencesRootPresenter.kt`, `features/preferences/impl/.../utils/ShowDeveloperSettingsProvider.kt`, `features/messages/impl/.../messagecomposer/MessageComposerPresenter.kt` (+ tests), `app/src/main/AndroidManifest.xml` |
| Mode clair seul | L'app reste en mode clair, comme le Web : « Apparence » retiré des réglages, un thème choisi avant n'est plus appliqué (`LeveragersConfig.USES_LIGHT_THEME_ONLY`) ; « Qualité d'envoi des médias » retiré, la qualité par défaut reste (`HAS_SIMPLIFIED_SETTINGS`) | `features/preferences/impl/.../root/PreferencesRootView.kt` (+ test), `libraries/designsystem/.../theme/ElementThemeApp.kt`, `libraries/designsystem/build.gradle.kts` |
| Message d'appel | « Agents » à la place d'« Element X » dans le message d'un appel non pris en charge, à l'affichage, dans toutes les langues (sans toucher aux traductions générées par Localazy) | `features/messages/impl/.../timeline/components/event/TimelineItemLegacyCallInviteView.kt` |
| Connexion automatique | L'écran d'accueil ouvre seul la page de connexion de chat.leveragers.com, une fois (installation, ou après déconnexion), comme un appui sur son bouton ; pas à l'ajout d'un compte ; si la page est fermée, l'écran et son bouton restent ; interrupteur `LeveragersConfig.OPENS_SIGN_IN_AUTOMATICALLY` | `features/login/impl/.../onboarding/OnBoardingPresenter.kt` |
| Création de salon | Nouveau salon dans le premier espace où l'on peut ajouter des salons (présélectionné), ouvert à ses membres, sans sections Accès ni Adresse ; sans espace, sur invitation ; pas de salon public (refusé par le Web et le serveur) ; la création d'un espace ne change pas ; interrupteur `LeveragersConfig.HAS_SIMPLIFIED_ROOM_CREATION` | `features/createroom/impl/.../configureroom/{ConfigureRoomPresenter,ConfigureRoomState,ConfigureRoomView,ConfigureRoomStatePreviewParam}.kt`, `features/createroom/impl/build.gradle.kts` (+ tests) |
| Palette | Couleurs du thème « Leveragers » de notre Element Web (fond crème `#FAF8F4`, encre bleu nuit `#0F1627` à la place du vert, texte secondaire `#6A645B`, bulles `#F1EDE6` / `#E7EDF6`, avatars), en thème clair, comme iOS : `LeveragersColors` (nouveau, dans compound) passé par `EnterpriseService.semanticColorsFlow` ; interrupteur `LeveragersConfig.HAS_LEVERAGERS_PALETTE` | `libraries/compound/.../colors/LeveragersColors.kt` (nouveau), `features/enterprise/impl-foss/.../DefaultEnterpriseService.kt` (+ test), `features/enterprise/impl-foss/build.gradle.kts`, `libraries/designsystem/.../theme/ColorAliases.kt` |

Restent, comme la politique de marque d'Element l'autorise : les mentions des vrais produits Element et les mentions
de droits d'auteur et de licence (le message d'un ancien appel nomme désormais « Agents », comme sur iOS). Le code est sous AGPL-3.0 (voir `LICENSE`) : ce dépôt public contient la source exacte de chaque version
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
- Permissions liées aux fonctions coupées (appels plein écran, installation d'APK) : à retirer avant le Play Store,
  pour éviter les déclarations correspondantes. Celles de la position sont déjà retirées ; le service de partage de
  position en direct reste déclaré (jamais lancé).
