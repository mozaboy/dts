/*
 * Copyright 2014-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.dts.resourcemanager.logmanager;

import java.sql.Blob;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import io.dts.common.exception.DtsException;
import io.dts.common.util.BlobUtil;
import io.dts.parser.struct.TxcRuntimeContext;
import io.dts.resourcemanager.struct.ContextStep2;

/**
 * @author liushiming
 * @version AbstractLogManager.java, v 0.0.1 2017年10月24日 下午3:48:10 liushiming
 */
public class DtsLogManagerImpl implements DtsLogManager {

  protected static DtsLogManager logManager = new DtsLogManagerImpl();

  private volatile BranchRollbackLogManager rollbackLogManager;

  private volatile BranchCommitLogManager commitLogManager;

  protected DtsLogManagerImpl() {}

  /**
   * 分支事务提交，仅删除UndoLog
   */
  @Override
  public void branchCommit(ContextStep2 context) throws SQLException {
    if (commitLogManager == null) {
      commitLogManager = new BranchCommitLogManager();
    }
    commitLogManager.branchCommit(context);
  }

  /**
   * 分支事务回滚，回滚阶段的数据库操作在一个本地事务中执行
   */
  @Override
  public void branchRollback(ContextStep2 context) throws SQLException {
    if (rollbackLogManager == null) {
      rollbackLogManager = new BranchRollbackLogManager();
    }
    rollbackLogManager.branchRollback(context);
  }

  protected TxcRuntimeContext getTxcRuntimeContexts(final long gid, final JdbcTemplate template) {
    String sql = String.format("select * from %s where status = 0 && " + "id = %d order by id desc",
        txcLogTableName, gid);
    List<TxcRuntimeContext> undos =
        SqlExecuteHelper.querySql(template, new RowMapper<TxcRuntimeContext>() {
          @Override
          public TxcRuntimeContext mapRow(ResultSet rs, int rowNum) throws SQLException {
            Blob blob = rs.getBlob("rollback_info");
            String str = BlobUtil.blob2string(blob);
            TxcRuntimeContext undoLogInfor = TxcRuntimeContext.decode(str);
            return undoLogInfor;
          }
        }, sql);
    if (undos == null) {
      return null;
    }
    if (undos.size() == 0) {
      return null;
    }
    if (undos.size() > 1) {
      throw new DtsException("check txc_undo_log, trx info duplicate");
    }
    return undos.get(0);
  }


}
