package com.diogotoporcov.filemanager.api.duplicate.persistence;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

class ImagePhashDuplicateCandidateRepositoryResourceTest {
    private PreparedStatement statement;
    private ResultSet resultSet;
    private Array chunkIndexes;
    private Array chunkValues;
    private ImagePhashDuplicateCandidateRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        DataSource dataSource = org.mockito.Mockito.mock(DataSource.class);
        Connection connection = org.mockito.Mockito.mock(Connection.class);
        statement = org.mockito.Mockito.mock(PreparedStatement.class);
        resultSet = org.mockito.Mockito.mock(ResultSet.class);
        chunkIndexes = org.mockito.Mockito.mock(Array.class);
        chunkValues = org.mockito.Mockito.mock(Array.class);
        repository = new ImagePhashDuplicateCandidateRepository(new JdbcTemplate(dataSource));

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(org.mockito.ArgumentMatchers.anyString())).thenReturn(statement);
        when(connection.createArrayOf(
                org.mockito.ArgumentMatchers.eq("smallint"),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(chunkIndexes);
        when(connection.createArrayOf(
                org.mockito.ArgumentMatchers.eq("integer"),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(chunkValues);
    }

    @Test
    void findCandidates_FreesArraysAndClosesStatementAndResultSetOnSuccess() throws Exception {
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(false);

        repository.findCandidates(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "0123456789abcdef",
                10,
                null,
                null,
                null,
                100);

        verify(resultSet).close();
        verify(statement).close();
        verify(chunkIndexes).free();
        verify(chunkValues).free();
    }

    @Test
    void findCandidates_FreesArraysAndClosesStatementWhenQueryFails() throws Exception {
        SQLException queryFailure = new SQLException("query failed");
        when(statement.executeQuery()).thenThrow(queryFailure);

        assertThatThrownBy(() -> repository.findCandidates(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "0123456789abcdef",
                10,
                null,
                null,
                null,
                100))
                .isInstanceOf(DataAccessException.class)
                .hasRootCause(queryFailure);

        verify(statement).close();
        verify(chunkIndexes).free();
        verify(chunkValues).free();
    }
}
