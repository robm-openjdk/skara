/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package org.openjdk.skara.bots.pr;

import org.openjdk.skara.bot.*;
import org.openjdk.skara.census.Contributor;
import org.openjdk.skara.forge.*;
import org.openjdk.skara.issuetracker.IssueProject;
import org.openjdk.skara.json.JSONValue;
import org.openjdk.skara.vcs.Hash;

import java.net.URI;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;
import java.util.regex.Pattern;

class PullRequestBot implements Bot {
    private final HostedRepository remoteRepo;
    private final HostedRepository censusRepo;
    private final String censusRef;
    private final LabelConfiguration labelConfiguration;
    private final Map<String, String> externalCommands;
    private final Map<String, String> blockingCheckLabels;
    private final Set<String> readyLabels;
    private final Set<String> twoReviewersLabels;
    private final Set<String> twentyFourHoursLabels;
    private final Map<String, Pattern> readyComments;
    private final IssueProject issueProject;
    private final boolean ignoreStaleReviews;
    private final Set<String> allowedIssueTypes;
    private final Pattern allowedTargetBranches;
    private final Path seedStorage;
    private final HostedRepository confOverrideRepo;
    private final String confOverrideName;
    private final String confOverrideRef;
    private final String censusLink;
    private final ConcurrentMap<Hash, Boolean> currentLabels;
    private final PullRequestUpdateCache updateCache;
    private final Logger log = Logger.getLogger("org.openjdk.skara.bots.pr");

    PullRequestBot(HostedRepository repo, HostedRepository censusRepo, String censusRef,
                   LabelConfiguration labelConfiguration, Map<String, String> externalCommands,
                   Map<String, String> blockingCheckLabels, Set<String> readyLabels,
                   Set<String> twoReviewersLabels, Set<String> twentyFourHoursLabels,
                   Map<String, Pattern> readyComments, IssueProject issueProject,
                   boolean ignoreStaleReviews, Set<String> allowedIssueTypes, Pattern allowedTargetBranches,
                   Path seedStorage, HostedRepository confOverrideRepo, String confOverrideName,
                   String confOverrideRef, String censusLink) {
        remoteRepo = repo;
        this.censusRepo = censusRepo;
        this.censusRef = censusRef;
        this.labelConfiguration = labelConfiguration;
        this.externalCommands = externalCommands;
        this.blockingCheckLabels = blockingCheckLabels;
        this.readyLabels = readyLabels;
        this.twoReviewersLabels = twoReviewersLabels;
        this.twentyFourHoursLabels = twentyFourHoursLabels;
        this.issueProject = issueProject;
        this.readyComments = readyComments;
        this.ignoreStaleReviews = ignoreStaleReviews;
        this.allowedIssueTypes = allowedIssueTypes;
        this.allowedTargetBranches = allowedTargetBranches;
        this.seedStorage = seedStorage;
        this.confOverrideRepo = confOverrideRepo;
        this.confOverrideName = confOverrideName;
        this.confOverrideRef = confOverrideRef;
        this.censusLink = censusLink;

        this.currentLabels = new ConcurrentHashMap<>();
        this.updateCache = new PullRequestUpdateCache();
    }

    static PullRequestBotBuilder newBuilder() {
        return new PullRequestBotBuilder();
    }

    private boolean isReady(PullRequest pr) {
        var labels = new HashSet<>(pr.labels());
        for (var readyLabel : readyLabels) {
            if (!labels.contains(readyLabel)) {
                log.fine("PR is not yet ready - missing label '" + readyLabel + "'");
                return false;
            }
        }

        var comments = pr.comments();
        for (var readyComment : readyComments.entrySet()) {
            var commentFound = false;
            for (var comment : comments) {
                if (comment.author().userName().equals(readyComment.getKey())) {
                    var matcher = readyComment.getValue().matcher(comment.body());
                    if (matcher.find()) {
                        commentFound = true;
                        break;
                    }
                }
            }
            if (!commentFound) {
                log.fine("PR is not yet ready - missing ready comment from '" + readyComment.getKey() +
                                 "containing '" + readyComment.getValue().pattern() + "'");
                return false;
            }
        }
        return true;
    }

    private List<WorkItem> getWorkItems(List<PullRequest> pullRequests) {
        var ret = new LinkedList<WorkItem>();

        for (var pr : pullRequests) {
            if (updateCache.needsUpdate(pr)) {
                if (!isReady(pr)) {
                    continue;
                }

                ret.add(new CheckWorkItem(this, pr, e -> updateCache.invalidate(pr)));
            }
        }

        return ret;
    }

    @Override
    public List<WorkItem> getPeriodicItems() {
        return getWorkItems(remoteRepo.pullRequests());
    }

    @Override
    public List<WorkItem> processWebHook(JSONValue body) {
        var webHook = remoteRepo.parseWebHook(body);
        if (webHook.isEmpty()) {
            return new ArrayList<>();
        }

        return getWorkItems(webHook.get().updatedPullRequests());
    }

    HostedRepository censusRepo() {
        return censusRepo;
    }

    String censusRef() {
        return censusRef;
    }

    LabelConfiguration labelConfiguration() {
        return labelConfiguration;
    }

    Map<String, String> externalCommands() {
        return externalCommands;
    }

    Map<String, String> blockingCheckLabels() {
        return blockingCheckLabels;
    }

    Set<String> twoReviewersLabels() {
        return twoReviewersLabels;
    }

    Set<String> twentyFourHoursLabels() {
        return twentyFourHoursLabels;
    }

    IssueProject issueProject() {
        return issueProject;
    }

    ConcurrentMap<Hash, Boolean> currentLabels() {
        return currentLabels;
    }

    boolean ignoreStaleReviews() {
        return ignoreStaleReviews;
    }

    Set<String> allowedIssueTypes() {
        return allowedIssueTypes;
    }

    Pattern allowedTargetBranches() {
        return allowedTargetBranches;
    }

    Optional<Path> seedStorage() {
        return Optional.ofNullable(seedStorage);
    }

    Optional<HostedRepository> confOverrideRepository() {
        return Optional.ofNullable(confOverrideRepo);
    }

    String confOverrideName() {
        return confOverrideName;
    }

    String confOverrideRef() {
        return confOverrideRef;
    }

    Optional<URI> censusLink(Contributor contributor) {
        if (censusLink == null) {
            return Optional.empty();
        }
        return Optional.of(URI.create(censusLink.replace("{{contributor}}", contributor.username())));
    }
}
