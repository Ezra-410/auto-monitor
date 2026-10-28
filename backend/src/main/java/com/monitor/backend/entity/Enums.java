package com.monitor.backend.entity;

public final class Enums {

    private Enums() {
    }

    public enum ObjectiveStatus {PLANNED, IN_PROGRESS, DONE, LATE, CANCELLED}

    public enum MilestoneStatus {PLANNED, DONE, MISSED}

    public enum TaskStatus {TODO, IN_PROGRESS, DONE, BLOCKED, CANCELLED}

    public enum TaskCreator {USER, AGENT}

    public enum BugSeverity {LOW, MEDIUM, HIGH, CRITICAL}

    public enum BugStatus {OPEN, TRIAGED, IN_PROGRESS, FIXED, CLOSED}

    public enum MessageAuthor {USER, AGENT, SYSTEM}
}
