/**
 * useJobsHistory — localStorage-backed session job history.
 *
 * Stores up to MAX_JOBS entries under "naukri.jobs.v1".
 * Newest entries are always at the front of the array.
 *
 * Created by: Adikarthik Gupta C B
 */
import { useState, useCallback } from "react";
import type { RunSummary } from "../screens/RunScreen";

const STORAGE_KEY = "naukri.jobs.v1";
const MAX_JOBS = 20;

export type JobStatus = "running" | "completed" | "stopped" | "failed";

export interface JobHistoryEntry {
  jobId: string;
  emails: string[];
  startedAt: number;
  status: JobStatus;
  summary?: RunSummary;
  outputFolder: string;
}

function loadFromStorage(): JobHistoryEntry[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return [];
    return JSON.parse(raw) as JobHistoryEntry[];
  } catch {
    return [];
  }
}

function saveToStorage(entries: JobHistoryEntry[]): void {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(entries));
  } catch {
    // localStorage may be unavailable in some Electron contexts — fail silently
  }
}

export interface JobsHistoryApi {
  jobs: JobHistoryEntry[];
  addJob(entry: Omit<JobHistoryEntry, "startedAt">): void;
  updateJobStatus(jobId: string, status: JobStatus, summary?: RunSummary): void;
}

export function useJobsHistory(): JobsHistoryApi {
  const [jobs, setJobs] = useState<JobHistoryEntry[]>(loadFromStorage);

  const addJob = useCallback((entry: Omit<JobHistoryEntry, "startedAt">): void => {
    setJobs(prev => {
      const newEntry: JobHistoryEntry = { ...entry, startedAt: Date.now() };
      // Prepend newest entry; cap at MAX_JOBS
      const updated = [newEntry, ...prev].slice(0, MAX_JOBS);
      saveToStorage(updated);
      return updated;
    });
  }, []);

  const updateJobStatus = useCallback((jobId: string, status: JobStatus, summary?: RunSummary): void => {
    setJobs(prev => {
      const updated = prev.map(job =>
        job.jobId === jobId
          ? { ...job, status, ...(summary !== undefined ? { summary } : {}) }
          : job
      );
      saveToStorage(updated);
      return updated;
    });
  }, []);

  return { jobs, addJob, updateJobStatus };
}
