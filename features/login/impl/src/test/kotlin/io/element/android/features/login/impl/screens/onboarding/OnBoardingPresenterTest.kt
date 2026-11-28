/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.login.impl.screens.onboarding

import com.google.common.truth.Truth.assertThat
import io.element.android.appconfig.OnBoardingConfig
import io.element.android.features.enterprise.api.EnterpriseService
import io.element.android.features.enterprise.api.IsEnterpriseBuild
import io.element.android.features.enterprise.test.FakeEnterpriseService
import io.element.android.features.login.impl.accesscontrol.DefaultAccountProviderAccessControl
import io.element.android.features.login.impl.accountprovider.AccountProviderDataSource
import io.element.android.features.login.impl.accountprovider.SaveAccountProviderToHistory
import io.element.android.features.login.impl.accountprovider.anAccountProviderDataSource
import io.element.android.features.login.impl.frontdoor.FakeFrontDoorLookup
import io.element.android.features.login.impl.frontdoor.FrontDoorLookup
import io.element.android.features.login.impl.frontdoor.FrontDoorResult
import io.element.android.features.login.impl.frontdoor.LastEmailStore
import io.element.android.features.login.impl.localnetwork.LocalNetworkPermissionGate
import io.element.android.features.login.impl.login.LoginModePresenter
import io.element.android.libraries.architecture.AsyncData
import io.element.android.libraries.core.meta.BuildMeta
import io.element.android.libraries.matrix.api.accountprovider.AccountProvider
import io.element.android.libraries.matrix.api.accountprovider.matrixOrgAccountProvider
import io.element.android.libraries.matrix.api.auth.MatrixAuthenticationService
import io.element.android.libraries.matrix.test.AN_ACCOUNT_PROVIDER
import io.element.android.libraries.matrix.test.AN_ACCOUNT_PROVIDER_2
import io.element.android.libraries.matrix.test.AN_ACCOUNT_PROVIDER_3
import io.element.android.libraries.matrix.test.AN_EXCEPTION
import io.element.android.libraries.matrix.test.A_HOMESERVER_URL
import io.element.android.libraries.matrix.test.A_HOMESERVER_URL_2
import io.element.android.libraries.matrix.test.A_LOGIN_HINT
import io.element.android.libraries.matrix.test.accountprovider.anAccountProviderManaged
import io.element.android.libraries.matrix.test.auth.FakeMatrixAuthenticationService
import io.element.android.libraries.matrix.test.auth.aMatrixHomeServerDetails
import io.element.android.libraries.matrix.test.core.aBuildMeta
import io.element.android.libraries.oauth.api.OAuthActionFlow
import io.element.android.libraries.oauth.test.FakeOAuthActionFlow
import io.element.android.libraries.permissions.api.PermissionsPresenter
import io.element.android.libraries.permissions.api.localnetwork.LocalNetworkPermissionAdvisor
import io.element.android.libraries.permissions.test.FakeLocalNetworkPermissionAdvisor
import io.element.android.libraries.permissions.test.FakePermissionsPresenterFactory
import io.element.android.libraries.preferences.test.FakePreferenceDataStoreFactory
import io.element.android.libraries.preferences.test.InMemoryAppPreferencesStore
import io.element.android.libraries.sessionstorage.api.SessionStore
import io.element.android.libraries.sessionstorage.test.InMemorySessionStore
import io.element.android.libraries.sessionstorage.test.aSessionData
import io.element.android.tests.testutils.WarmUpRule
import io.element.android.tests.testutils.consumeItemsUntilPredicate
import io.element.android.tests.testutils.test
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class OnBoardingPresenterTest {
    @get:Rule
    val warmUpRule = WarmUpRule()

    companion object {
        private const val ACCOUNT_PROVIDER_FROM_LINK = AN_ACCOUNT_PROVIDER
        private const val ACCOUNT_PROVIDER_FROM_CONFIG = AN_ACCOUNT_PROVIDER_2
        private const val ACCOUNT_PROVIDER_FROM_CONFIG_2 = AN_ACCOUNT_PROVIDER_3
    }

    @Test
    fun `present - ensure initial conditions`() {
        assertThat(
            setOf(
                ACCOUNT_PROVIDER_FROM_LINK,
                ACCOUNT_PROVIDER_FROM_CONFIG,
                ACCOUNT_PROVIDER_FROM_CONFIG_2,
            ).size
        ).isEqualTo(3)
    }

    @Test
    fun `present - initial state`() = runTest {
        val buildMeta = aBuildMeta(
            applicationName = "A",
            productionApplicationName = "B",
            desktopApplicationName = "C",
        )
        val presenter = createPresenter(
            buildMeta = buildMeta,
            enterpriseService = FakeEnterpriseService(
                accountProviderAllowListResult = { listOf(anAccountProviderManaged(serverName = ACCOUNT_PROVIDER_FROM_CONFIG)) },
                canConnectToAnyAccountProviderResult = { true },
            ),
        )
        presenter.test {
            val initialState = awaitItem()
            assertThat(initialState.showBackButton).isFalse()
            assertThat(initialState.defaultAccountProvider).isNull()
            assertThat(initialState.canLoginWithQrCode).isFalse()
            assertThat(initialState.productionApplicationName).isEqualTo("B")
            assertThat(initialState.canCreateAccount).isEqualTo(OnBoardingConfig.CAN_CREATE_ACCOUNT)
            assertThat(initialState.canReportBug).isFalse()
            assertThat(initialState.isAddingAccount).isFalse()
            val finalState = awaitItem()
            assertThat(finalState.canLoginWithQrCode).isEqualTo(OnBoardingConfig.CAN_LOGIN_WITH_QR_CODE)
        }
    }

    @Test
    fun `present - initial state with back button`() = runTest {
        val presenter = createPresenter(
            params = OnBoardingNode.Params(
                accountProvider = null,
                loginHint = null,
                showBackButton = true,
            ),
        )
        presenter.test {
            val initialState = awaitItem()
            assertThat(initialState.showBackButton).isTrue()
            skipItems(1)
        }
    }

    @Test
    fun `present - initial state adding account`() = runTest {
        val presenter = createPresenter(
            sessionStore = InMemorySessionStore(
                initialList = listOf(
                    aSessionData()
                )
            )
        )
        presenter.test {
            skipItems(1)
            val initialState = awaitItem()
            assertThat(initialState.isAddingAccount).isTrue()
        }
    }

    @Test
    fun `present - on boarding logo`() = runTest {
        val presenter = createPresenter(
            onBoardingLogoResIdProvider = OnBoardingLogoResIdProvider { 42 },
        )
        presenter.test {
            skipItems(1)
            val initialState = awaitItem()
            assertThat(initialState.onBoardingLogoResId).isEqualTo(42)
        }
    }

    @Test
    fun `present - clicking on version 7 times has no effect if rageshake not available`() = runTest {
        val presenter = createPresenter(
            rageshakeFeatureAvailability = { flowOf(false) },
        )
        presenter.test {
            skipItems(1)
            awaitItem().also { state ->
                assertThat(state.canReportBug).isFalse()
                repeat(7) {
                    state.eventSink(OnBoardingEvent.OnVersionClick)
                }
            }
            expectNoEvents()
        }
    }

    @Test
    fun `present - clicking on version 7 times will reveal the report a problem button`() = runTest {
        val presenter = createPresenter()
        presenter.test {
            skipItems(1)
            awaitItem().also { state ->
                assertThat(state.canReportBug).isFalse()
                repeat(7) {
                    state.eventSink(OnBoardingEvent.OnVersionClick)
                }
            }
            assertThat(awaitItem().canReportBug).isTrue()
        }
    }

    @Test
    fun `present - opening the app using link with allowed account provider, and the app does not force account provider`() = runTest {
        val presenter = createPresenter(
            params = OnBoardingNode.Params(
                accountProvider = ACCOUNT_PROVIDER_FROM_LINK,
                loginHint = null,
                showBackButton = false,
            ),
            enterpriseService = FakeEnterpriseService(
                accountProviderAllowListResult = { listOf(anAccountProviderManaged(serverName = ACCOUNT_PROVIDER_FROM_CONFIG)) },
                canConnectToAnyAccountProviderResult = { true },
                isAllowedToConnectToAccountProviderResult = { true },
                isElementProEnforcedResult = { false },
            ),
        )
        presenter.test {
            skipItems(3)
            awaitItem().also {
                assertThat(it.defaultAccountProvider).isEqualTo(AccountProvider.Generic(ACCOUNT_PROVIDER_FROM_LINK))
                assertThat(it.canLoginWithQrCode).isFalse()
                assertThat(it.canCreateAccount).isFalse()
            }
        }
    }

    @Test
    fun `present - opening the app using link with not allowed account provider, and the app does not force account provider`() = runTest {
        val presenter = createPresenter(
            params = OnBoardingNode.Params(
                accountProvider = ACCOUNT_PROVIDER_FROM_LINK,
                loginHint = null,
                showBackButton = false,
            ),
            enterpriseService = FakeEnterpriseService(
                accountProviderAllowListResult = {
                    listOf(
                        anAccountProviderManaged(serverName = ACCOUNT_PROVIDER_FROM_CONFIG),
                        anAccountProviderManaged(serverName = ACCOUNT_PROVIDER_FROM_CONFIG_2)
                    )
                },
                canConnectToAnyAccountProviderResult = { false },
                isAllowedToConnectToAccountProviderResult = { false },
            ),
        )
        presenter.test {
            skipItems(1)
            awaitItem().also {
                assertThat(it.defaultAccountProvider).isNull()
                assertThat(it.canLoginWithQrCode).isEqualTo(OnBoardingConfig.CAN_LOGIN_WITH_QR_CODE)
                assertThat(it.canCreateAccount).isFalse()
            }
        }
    }

    @Test
    fun `present - opening the app using link, and the app forces account provider`() = runTest {
        val presenter = createPresenter(
            params = OnBoardingNode.Params(
                accountProvider = ACCOUNT_PROVIDER_FROM_LINK,
                loginHint = null,
                showBackButton = false,
            ),
            enterpriseService = FakeEnterpriseService(
                accountProviderAllowListResult = { listOf(anAccountProviderManaged(serverName = ACCOUNT_PROVIDER_FROM_CONFIG)) },
                canConnectToAnyAccountProviderResult = { false },
            )
        )
        presenter.test {
            skipItems(1)
            awaitItem().also {
                assertThat(it.defaultAccountProvider).isEqualTo(anAccountProviderManaged(serverName = ACCOUNT_PROVIDER_FROM_CONFIG))
                assertThat(it.canLoginWithQrCode).isEqualTo(OnBoardingConfig.CAN_LOGIN_WITH_QR_CODE)
                assertThat(it.canCreateAccount).isFalse()
            }
        }
    }

    @Test
    fun `present - a single configured account provider is not forced when the user may use another one`() = runTest {
        // A singleton allow list only forces the account provider when the user is not free to use another one.
        val presenter = createPresenter(
            enterpriseService = FakeEnterpriseService(
                accountProviderAllowListResult = { listOf(anAccountProviderManaged(serverName = ACCOUNT_PROVIDER_FROM_CONFIG)) },
                canConnectToAnyAccountProviderResult = { true },
            ),
        )
        presenter.test {
            awaitItem().also {
                assertThat(it.defaultAccountProvider).isNull()
                assertThat(it.mustChooseAccountProvider).isFalse()
            }
            skipItems(1)
        }
    }

    @Test
    fun `present - the user must choose when several account providers are configured and enforced`() = runTest {
        val presenter = createPresenter(
            enterpriseService = FakeEnterpriseService(
                accountProviderAllowListResult = {
                    listOf(
                        anAccountProviderManaged(serverName = ACCOUNT_PROVIDER_FROM_CONFIG),
                        anAccountProviderManaged(serverName = ACCOUNT_PROVIDER_FROM_CONFIG_2)
                    )
                },
                canConnectToAnyAccountProviderResult = { false },
            ),
        )
        presenter.test {
            awaitItem().also {
                assertThat(it.defaultAccountProvider).isNull()
                assertThat(it.mustChooseAccountProvider).isTrue()
            }
            skipItems(1)
        }
    }

    @Test
    fun `present - default account provider - login and clear error`() = runTest {
        val authenticationService = FakeMatrixAuthenticationService(
            setHomeserverResult = {
                Result.failure(AN_EXCEPTION)
            },
        )
        val accountProviderDataSource = anAccountProviderDataSource()
        val presenter = createPresenter(
            params = OnBoardingNode.Params(
                accountProvider = A_HOMESERVER_URL,
                loginHint = A_LOGIN_HINT,
                showBackButton = false,
            ),
            enterpriseService = FakeEnterpriseService(
                isAllowedToConnectToAccountProviderResult = { true },
                isElementProEnforcedResult = { false },
            ),
            loginModePresenter = createLoginModePresenter(
                authenticationService = authenticationService,
            ),
            accountProviderDataSource = accountProviderDataSource,
        )
        presenter.test {
            skipItems(3)
            awaitItem().also {
                assertThat(it.defaultAccountProvider).isEqualTo(AccountProvider.Generic(A_HOMESERVER_URL))
                assertThat(accountProviderDataSource.flow.first()).isEqualTo(matrixOrgAccountProvider)
                it.eventSink(OnBoardingEvent.OnSignIn(AccountProvider.Generic(A_HOMESERVER_URL_2)))
                skipItems(1) // Loading
                // Account data source has been updated
                assertThat(accountProviderDataSource.flow.first()).isEqualTo(AccountProvider.Generic(A_HOMESERVER_URL_2))
                // Check an error was returned
                val submittedState = awaitItem()
                assertThat(submittedState.loginModeState.loginMode).isInstanceOf(AsyncData.Failure::class.java)

                // Assert the error is then cleared
                submittedState.eventSink(OnBoardingEvent.ClearError)
                val clearedState = awaitItem()
                assertThat(clearedState.loginModeState.loginMode).isEqualTo(AsyncData.Uninitialized)
            }
        }
    }

    @Test
    fun `present - email sign-in - the field starts empty`() = runTest {
        val presenter = createPresenter()
        presenter.test {
            assertThat(awaitItem().emailSignIn).isEqualTo(anEmailSignInState())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - email sign-in - the last email sent pre-fills the field`() = runTest {
        val lastEmailStore = aLastEmailStore()
        lastEmailStore.set("bob@example.com")
        val presenter = createPresenter(lastEmailStore = lastEmailStore)
        presenter.test {
            val state = consumeItemsUntilPredicate { it.emailSignIn?.email == "bob@example.com" }.last()
            assertThat(state.emailSignIn?.error).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - email sign-in - the server of the front door starts the sign-in with the email as login hint`() = runTest {
        val setHomeserverCalls = mutableListOf<String>()
        val authenticationService = FakeMatrixAuthenticationService(
            setHomeserverResult = {
                setHomeserverCalls.add(it)
                Result.success(aMatrixHomeServerDetails(supportsOAuthLogin = true))
            },
        )
        val lookups = mutableListOf<String>()
        val guardedServers = mutableListOf<AccountProvider>()
        val lastEmailStore = aLastEmailStore()
        val accountProviderDataSource = anAccountProviderDataSource()
        val presenter = createPresenter(
            enterpriseService = FakeEnterpriseService(
                isAllowedToConnectToAccountProviderResult = {
                    guardedServers.add(it)
                    true
                },
            ),
            loginModePresenter = createLoginModePresenter(
                authenticationService = authenticationService,
            ),
            accountProviderDataSource = accountProviderDataSource,
            frontDoorLookup = FakeFrontDoorLookup { email ->
                lookups.add(email)
                FrontDoorResult.Server("theseis.chat.leveragers.com")
            },
            lastEmailStore = lastEmailStore,
        )
        presenter.test {
            awaitItem().eventSink(OnBoardingEvent.SetEmail(" alice@theseis.fr "))
            consumeItemsUntilPredicate { it.emailSignIn?.email == " alice@theseis.fr " }.last()
                .eventSink(OnBoardingEvent.SubmitEmail)
            consumeItemsUntilPredicate { it.loginModeState.loginMode is AsyncData.Success }
            assertThat(lookups).containsExactly("alice@theseis.fr")
            assertThat(guardedServers).containsExactly(AccountProvider.Generic("theseis.chat.leveragers.com"))
            assertThat(setHomeserverCalls).containsExactly("theseis.chat.leveragers.com")
            assertThat(authenticationService.getOAuthUrlLoginHint).isEqualTo("alice@theseis.fr")
            assertThat(accountProviderDataSource.flow.first()).isEqualTo(AccountProvider.Generic("theseis.chat.leveragers.com"))
            assertThat(lastEmailStore.get()).isEqualTo("alice@theseis.fr")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - email sign-in - a front door error keeps the email and does not sign in`() = runTest {
        val lastEmailStore = aLastEmailStore()
        val presenter = createPresenter(
            frontDoorLookup = FakeFrontDoorLookup { FrontDoorResult.Unreachable },
            lastEmailStore = lastEmailStore,
        )
        presenter.test {
            awaitItem().eventSink(OnBoardingEvent.SetEmail("alice@example.com"))
            consumeItemsUntilPredicate { it.emailSignIn?.email == "alice@example.com" }.last()
                .eventSink(OnBoardingEvent.SubmitEmail)
            val state = consumeItemsUntilPredicate {
                it.emailSignIn?.error != null && it.emailSignIn?.isLookingUp == false
            }.last()
            assertThat(state.emailSignIn).isEqualTo(
                anEmailSignInState(email = "alice@example.com", error = EmailSignInError.FrontDoorUnreachable)
            )
            assertThat(state.loginModeState.loginMode).isEqualTo(AsyncData.Uninitialized)
            assertThat(lastEmailStore.get()).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - email sign-in - a server refused by the guard is a front door error`() = runTest {
        val presenter = createPresenter(
            enterpriseService = FakeEnterpriseService(
                isAllowedToConnectToAccountProviderResult = { false },
            ),
            frontDoorLookup = FakeFrontDoorLookup { FrontDoorResult.Server("matrix.org") },
        )
        presenter.test {
            awaitItem().eventSink(OnBoardingEvent.SetEmail("alice@example.com"))
            consumeItemsUntilPredicate { it.emailSignIn?.email == "alice@example.com" }.last()
                .eventSink(OnBoardingEvent.SubmitEmail)
            val state = consumeItemsUntilPredicate {
                it.emailSignIn?.error != null && it.emailSignIn?.isLookingUp == false
            }.last()
            assertThat(state.emailSignIn?.error).isEqualTo(EmailSignInError.FrontDoorUnreachable)
            assertThat(state.loginModeState.loginMode).isEqualTo(AsyncData.Uninitialized)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - email sign-in - an invalid email shows the error until the email changes`() = runTest {
        val presenter = createPresenter(
            frontDoorLookup = FakeFrontDoorLookup { FrontDoorResult.InvalidEmail },
        )
        presenter.test {
            awaitItem().eventSink(OnBoardingEvent.SetEmail("alice@"))
            consumeItemsUntilPredicate { it.emailSignIn?.email == "alice@" }.last()
                .eventSink(OnBoardingEvent.SubmitEmail)
            consumeItemsUntilPredicate { it.emailSignIn?.error == EmailSignInError.InvalidEmail }.last()
                .eventSink(OnBoardingEvent.SetEmail("alice@example.com"))
            val state = consumeItemsUntilPredicate { it.emailSignIn?.email == "alice@example.com" }.last()
            assertThat(state.emailSignIn?.error).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }
}

private fun aLastEmailStore() = LastEmailStore(FakePreferenceDataStoreFactory())

private fun createPresenter(
    params: OnBoardingNode.Params = OnBoardingNode.Params(
        accountProvider = null,
        loginHint = null,
        showBackButton = false,
    ),
    buildMeta: BuildMeta = aBuildMeta(),
    enterpriseService: EnterpriseService = FakeEnterpriseService(),
    isEnterpriseBuild: IsEnterpriseBuild = { false },
    rageshakeFeatureAvailability: () -> Flow<Boolean> = { flowOf(true) },
    loginModePresenter: LoginModePresenter = createLoginModePresenter(),
    onBoardingLogoResIdProvider: OnBoardingLogoResIdProvider = OnBoardingLogoResIdProvider { null },
    sessionStore: SessionStore = InMemorySessionStore(),
    accountProviderDataSource: AccountProviderDataSource = anAccountProviderDataSource(),
    frontDoorLookup: FrontDoorLookup = FakeFrontDoorLookup(),
    lastEmailStore: LastEmailStore = aLastEmailStore(),
) = OnBoardingPresenter(
    params = params,
    buildMeta = buildMeta,
    enterpriseService = enterpriseService,
    defaultAccountProviderAccessControl = DefaultAccountProviderAccessControl(
        enterpriseService = enterpriseService,
        isEnterpriseBuild = isEnterpriseBuild,
    ),
    rageshakeFeatureAvailability = rageshakeFeatureAvailability,
    loginModePresenter = loginModePresenter,
    onBoardingLogoResIdProvider = onBoardingLogoResIdProvider,
    sessionStore = sessionStore,
    accountProviderDataSource = accountProviderDataSource,
    frontDoorLookup = frontDoorLookup,
    lastEmailStore = lastEmailStore,
)

fun createLoginModePresenter(
    oAuthActionFlow: OAuthActionFlow = FakeOAuthActionFlow(),
    authenticationService: MatrixAuthenticationService = FakeMatrixAuthenticationService(),
    localNetworkPermissionAdvisor: LocalNetworkPermissionAdvisor =
        FakeLocalNetworkPermissionAdvisor(),
    permissionsPresenterFactory: PermissionsPresenter.Factory =
        FakePermissionsPresenterFactory(),
    saveAccountProviderToHistory: SaveAccountProviderToHistory =
        SaveAccountProviderToHistory(anAccountProviderDataSource(), InMemoryAppPreferencesStore()),
): LoginModePresenter = LoginModePresenter(
    oAuthActionFlow = oAuthActionFlow,
    authenticationService = authenticationService,
    localNetworkPermissionGate = LocalNetworkPermissionGate(
        advisor = localNetworkPermissionAdvisor,
        permissionsPresenterFactory = permissionsPresenterFactory,
    ),
    saveAccountProviderToHistory = saveAccountProviderToHistory,
)
