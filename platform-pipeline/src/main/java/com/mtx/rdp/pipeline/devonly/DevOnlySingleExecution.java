package com.mtx.rdp.pipeline.devonly;

import com.mtx.rdp.core.contract.ContractVersionUniqueness;
import com.mtx.rdp.core.contract.ContractVersionUniquenessQuery;
import com.mtx.rdp.core.contract.ContractVersionUniquenessResult;
import com.mtx.rdp.core.contract.InternalContractVersionId;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * 一次 DEV_ONLY 处理。不选择快照，不访问数据库，不依赖 Transform 或 Writer 模块。
 */
public final class DevOnlySingleExecution {

    public DevOnlyExecutionResult execute(
            DevOnlyFixture fixture,
            UnaryOperator<String> passthrough,
            Function<DevOnlyWriteCall, DevOnlyWriteAnswer> writer) {
        if (fixture == null || passthrough == null || writer == null) {
            throw new IllegalArgumentException("DEV_ONLY 执行输入不能为空");
        }
        if (!fixture.devOnly()) {
            return new DevOnlyExecutionResult.RejectedNotDevOnly();
        }
        if (fixture.snapshotId() == null || fixture.snapshotId().isBlank() || fixture.entries().isEmpty()) {
            return new DevOnlyExecutionResult.NotExecuted("夹具没有已固定快照，未执行");
        }
        ContractVersionUniquenessResult resolved = ContractVersionUniqueness.resolve(new ContractVersionUniquenessQuery(
                fixture.snapshotId(),
                fixture.externalVersion(),
                InternalContractVersionId.absent(),
                fixture.contractFamilyId(),
                fixture.entries()));
        if (!(resolved instanceof ContractVersionUniquenessResult.Resolved unique)) {
            return new DevOnlyExecutionResult.Unresolved(ContractVersionUniquenessResult.CONTRACT_VERSION_UNRESOLVED);
        }
        String passed = passthrough.apply(fixture.fieldText());
        DevOnlyWriteAnswer answer = writer.apply(new DevOnlyWriteCall(unique.contractVersionId(), passed));
        if (!answer.success()) {
            return new DevOnlyExecutionResult.WriteFailed(answer.failureMessage());
        }
        return new DevOnlyExecutionResult.Completed(unique.contractVersionId(), passed);
    }
}
