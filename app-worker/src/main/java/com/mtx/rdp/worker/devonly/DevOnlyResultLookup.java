package com.mtx.rdp.worker.devonly;

@FunctionalInterface
interface DevOnlyResultLookup {

    DevOnlyDorisQuery.ResultRow find(String devRunId);
}
