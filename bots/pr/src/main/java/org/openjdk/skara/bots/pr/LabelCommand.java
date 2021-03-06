/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

import org.openjdk.skara.forge.*;
import org.openjdk.skara.issuetracker.Comment;

import java.io.*;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;

public class LabelCommand implements CommandHandler {
    private final String commandName;

    private static final Pattern argumentPattern = Pattern.compile("(?:(add|remove)\\s+)?((?:[A-Za-z0-9_@.-]+[\\s,]*)+)");
    private static final Pattern ignoredSuffixes = Pattern.compile("^(.*)(?:-dev(?:@openjdk.java.net)?)$");

    LabelCommand() {
        this("label");
    }

    LabelCommand(String commandName) {
        this.commandName = commandName;
    }

    private void showHelp(LabelConfiguration labelConfiguration, PrintWriter reply) {
        reply.println("Usage: `/" + commandName + "` <add|remove> [label[, label, ...]]` where `label` is an additional classification that should " +
                              "be applied to this PR. These labels are valid:");
        labelConfiguration.allowed().forEach(label -> reply.println(" * `" + label + "`"));
    }

    @Override
    public void handle(PullRequestBot bot, PullRequest pr, CensusInstance censusInstance, Path scratchPath, CommandInvocation command, List<Comment> allComments, PrintWriter reply) {
        if (!command.user().equals(pr.author()) && (!censusInstance.isCommitter(command.user()))) {
            reply.println("Only the PR author and project [Committers](https://openjdk.java.net/bylaws#committer) are allowed to modify labels on a PR.");
            return;
        }

        var argumentMatcher = argumentPattern.matcher(command.args());
        if (!argumentMatcher.matches()) {
            showHelp(bot.labelConfiguration(), reply);
            return;
        }

        var labels = argumentMatcher.group(2).split("[\\s,]+");
        for (int i = 0; i < labels.length; ++i) {
            var label = labels[i];
            var ignoredSuffixMatcher = ignoredSuffixes.matcher(label);
            if (ignoredSuffixMatcher.matches()) {
                label = ignoredSuffixMatcher.group(1);
                labels[i] = label;
            }
            if (!bot.labelConfiguration().allowed().contains(label)) {
                reply.println("The label `" + label + "` is not a valid label. These labels are valid:");
                bot.labelConfiguration().allowed().forEach(l -> reply.println(" * `" + l + "`"));
                return;
            }
        }
        if (labels.length == 0) {
            showHelp(bot.labelConfiguration(), reply);
            return;
        }
        var currentLabels = new HashSet<>(pr.labels());
        if (argumentMatcher.group(1) == null || argumentMatcher.group(1).equals("add")) {
            for (var label : labels) {
                if (!currentLabels.contains(label)) {
                    pr.addLabel(label);
                    reply.println(LabelTracker.addLabelMarker(label));
                    reply.println("The `" + label + "` label was successfully added.");
                } else {
                    reply.println("The `" + label + "` label was already applied.");
                }
            }
        } else if (argumentMatcher.group(1).equals("remove")) {
            for (var label : labels) {
                if (currentLabels.contains(label)) {
                    pr.removeLabel(label);
                    reply.println(LabelTracker.removeLabelMarker(label));
                    reply.println("The `" + label + "` label was successfully removed.");
                } else {
                    reply.println("The `" + label + "` label was not set.");
                }
            }
        }
    }

    @Override
    public String description() {
        return "add or remove an additional classification label";
    }

    @Override
    public boolean allowedInBody() {
        return true;
    }
}
