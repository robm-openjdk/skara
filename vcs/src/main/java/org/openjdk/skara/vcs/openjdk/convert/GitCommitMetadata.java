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
package org.openjdk.skara.vcs.openjdk.convert;

import org.openjdk.skara.vcs.*;

import java.time.*;
import java.util.List;

class GitCommitMetadata {
    private final int mark;
    private final List<Integer> parents;

    private final Author author;
    private final Author committer;

    private final Branch branch;

    private final ZonedDateTime date;

    private final String message;

    public GitCommitMetadata(int mark,
                             List<Integer> parents,
                             Author author,
                             Author committer,
                             Branch branch,
                             ZonedDateTime date,
                             String message) {
        this.mark = mark;
        this.parents = parents;
        this.author = author;
        this.committer = committer;
        this.branch = branch;
        this.date = date;
        this.message = message;
    }

    public int mark() {
        return mark;
    }

    public List<Integer> parents() {
        return parents;
    }

    public Author author() {
        return author;
    }

    public Author committer() {
        return committer;
    }

    public Branch branch() {
        return branch;
    }

    public ZonedDateTime date() {
        return date;
    }

    public String message() {
        return message;
    }
}
