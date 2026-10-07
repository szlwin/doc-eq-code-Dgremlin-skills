package com.dec.lite.directory;

import com.dec.lite.information.InformationKey;

/** Classification progresses from result directory to a selected case. */
public record CaseEdge(String parent, String target, InformationKey informationRef) { }
