/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jetbrains.buildServer.configs.kotlin.vcs

import jetbrains.buildServer.configs.kotlin.v0.KVcsRoot

open class GitVcsRoot : KVcsRoot {

    constructor(init: GitVcsRoot.() -> Unit = {}, base: GitVcsRoot? = null): super(base = base as KVcsRoot?) {
        type = "jetbrains.git"
        init()
    }

    var url by stringParameter()

    var pushUrl by stringParameter("push_url")

    var branch by stringParameter()

    var branchSpec by stringParameter("teamcity:branchSpec")

    var useTagsAsBranches by booleanParameter("reportTagRevisions")

    var userNameStyle by enumParameter<UserNameStyle>("usernameStyle")

    var checkoutSubmodules by enumParameter<CheckoutSubmodules>("submoduleCheckout")

    var userForTags by stringParameter()

    var serverSideAutoCRLF by booleanParameter("serverSideAutoCrlf")

    var agentGitPath by stringParameter()

    var agentCleanPolicy by enumParameter<AgentCleanPolicy>("agentCleanPolicy")

    var agentCleanFilesPolicy by enumParameter<AgentCleanFilesPolicy>("agentCleanFilesPolicy")

    var useMirrors by booleanParameter("useAlternates")

    var authMethod by enumParameter<AuthMethod>()

    var userName by stringParameter("username")

    var password by stringParameter("secure:password")

    var uploadedKey by stringParameter("teamcitySshKey")

    var customKeyPath by stringParameter("privateKeyPath")

    var passphrase by stringParameter("secure:passphrase")

    enum class AgentCleanPolicy {
        NEVER,
        ALWAYS,
        ON_BRANCH_CHANGE
    }

    enum class AgentCleanFilesPolicy {
        IGNORED_ONLY,
        NON_IGNORED_ONLY,
        ALL_UNTRACKED
    }

    enum class UserNameStyle {
        NAME,
        USERID,
        EMAIL,
        FULL
    }

    enum class CheckoutSubmodules {
        SUBMODULES_CHECKOUT,
        IGNORE
    }

    enum class AuthMethod {
        ANONYMOUS,
        PASSWORD,
        TEAMCITY_SSH_KEY,
        PRIVATE_KEY_DEFAULT,
        PRIVATE_KEY_FILE
    }
}
