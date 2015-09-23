package com.vladmihalcea.book.high_performance_java_persistence.jdbc.fetching;

import com.vladmihalcea.book.high_performance_java_persistence.jdbc.batch.providers.BatchEntityProvider;
import com.vladmihalcea.hibernate.masterclass.laboratory.util.DataSourceProviderIntegrationTest;
import org.hibernate.dialect.pagination.LimitHandler;
import org.hibernate.engine.spi.RowSelection;
import org.hibernate.internal.SessionFactoryImpl;
import org.junit.Test;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * ResultSetLimitTest - Test limiting result set vs fetching and discarding rows
 *
 * @author Vlad Mihalcea
 */
public class ResultSetLimitTest extends DataSourceProviderIntegrationTest {
    public static final String INSERT_POST = "insert into post (title, version, id) values (?, ?, ?)";

    public static final String INSERT_POST_COMMENT = "insert into post_comment (post_id, review, version, id) values (?, ?, ?, ?)";

    public static final String SELECT_POST_COMMENT =
            "select pc.id as pc_id, p.id as p_id  " +
            "from post_comment pc " +
            "inner join Post p on p.id = pc.post_id ";

    private BatchEntityProvider entityProvider = new BatchEntityProvider();

    public ResultSetLimitTest(DataSourceProvider dataSourceProvider) {
        super(dataSourceProvider);
    }

    @Override
    protected Class<?>[] entities() {
        return entityProvider.entities();
    }

    @Override
    public void init() {
        super.init();
        doInConnection(connection -> {
            try (
                    PreparedStatement postStatement = connection.prepareStatement(INSERT_POST);
                    PreparedStatement postCommentStatement = connection.prepareStatement(INSERT_POST_COMMENT);
            ) {
                int postCount = getPostCount();
                int postCommentCount = getPostCommentCount();

                int index;

                for (int i = 0; i < postCount; i++) {
                    index = 0;
                    postStatement.setString(++index, String.format("Post no. %1$d", i));
                    postStatement.setInt(++index, 0);
                    postStatement.setLong(++index, i);
                    postStatement.addBatch();
                    if(i % 100 == 0) {
                        postStatement.executeBatch();
                    }
                }
                postStatement.executeBatch();

                for (int i = 0; i < postCount; i++) {
                    for (int j = 0; j < postCommentCount; j++) {
                        index = 0;
                        postCommentStatement.setLong(++index, i);
                        postCommentStatement.setString(++index, String.format("Post comment %1$d", j));
                        postCommentStatement.setInt(++index, (int) (Math.random() * 1000));
                        postCommentStatement.setLong(++index, (postCommentCount * i) + j);
                        postCommentStatement.addBatch();
                        if(j % 100 == 0) {
                            postCommentStatement.executeBatch();
                        }
                    }
                }
                postCommentStatement.executeBatch();
            } catch (SQLException e) {
                fail(e.getMessage());
            }
        });
    }

    @Test
    public void testNoLimit() {
        long startNanos = System.nanoTime();
        doInConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    SELECT_POST_COMMENT
            )) {
                statement.execute();
                ResultSet resultSet = statement.getResultSet();
                int count = 0;
                while (resultSet.next()) {
                    resultSet.getLong(1);
                    count++;
                }
                assertEquals(getPostCount() * getPostCommentCount(), count);
            } catch (SQLException e) {
                fail(e.getMessage());
            }

        });
        LOGGER.info("{} Result Set without limit took {} millis",
                getDataSourceProvider().database(),
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos));
    }

    @Test
    public void testLimit() {
        RowSelection rowSelection = new RowSelection();
        rowSelection.setMaxRows(getMaxRows());
        LimitHandler limitHandler = ((SessionFactoryImpl) getSessionFactory()).getDialect().buildLimitHandler(SELECT_POST_COMMENT, rowSelection);
        long startNanos = System.nanoTime();
        doInConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    limitHandler.getProcessedSql()
            )) {
                LOGGER.info(limitHandler.getProcessedSql());
                statement.setInt(1, getMaxRows());
                statement.execute();
                int count = 0;
                ResultSet resultSet = statement.getResultSet();
                while (resultSet.next()) {
                    resultSet.getLong(1);
                    count++;
                }
                assertEquals(getMaxRows(), count);
            } catch (SQLException e) {
                fail(e.getMessage());
            }

        });
        LOGGER.info("{} Result Set with limit took {} millis",
                getDataSourceProvider().database(),
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos));
    }

    @Test
    public void testMaxSize() {
        long startNanos = System.nanoTime();
        doInConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    SELECT_POST_COMMENT
            )) {
                statement.setMaxRows(getMaxRows());
                statement.execute();
                ResultSet resultSet = statement.getResultSet();
                int count = 0;
                while (resultSet.next()) {
                    resultSet.getLong(1);
                    count++;
                }
                assertEquals(getMaxRows(), count);
            } catch (SQLException e) {
                fail(e.getMessage());
            }

        });
        LOGGER.info("{} Result Set maxSize took {} millis",
                getDataSourceProvider().database(),
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos));
    }

    protected int getPostCount() {
        return 100000;
    }

    protected int getPostCommentCount() {
        return 10;
    }

    protected int getMaxRows() {
        return 100;
    }


    @Override
    protected boolean proxyDataSource() {
        return false;
    }
}
