/**
 * Copyright (c) 2000-present Liferay, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */

package com.liferay.message.boards.internal.upgrade.v3_1_0;

import com.liferay.message.boards.internal.upgrade.v3_1_0.util.MBMessageTable;
import com.liferay.petra.string.StringBundler;
import com.liferay.petra.string.StringPool;
import com.liferay.portal.kernel.dao.jdbc.AutoBatchPreparedStatementUtil;
import com.liferay.portal.kernel.upgrade.UpgradeProcess;
import com.liferay.portal.kernel.util.FriendlyURLNormalizerUtil;
import com.liferay.portal.kernel.util.StringUtil;
import com.liferay.portal.kernel.util.Validator;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author Javier Gamarra
 */
public class UpgradeUrlSubject extends UpgradeProcess {

	@Override
	protected void doUpgrade() throws Exception {
		if (!hasColumn("MBMessage", "urlSubject")) {
			alter(
				MBMessageTable.class,
				new AlterColumnType("urlSubject", "VARCHAR(255) null"));
		}

		_populateUrlSubject();
	}

	private String _findUniqueUrlSubject(long mbMessageId, String subject) {
		String urlSubject = _getUrlSubject(mbMessageId, subject);

		String uniqueUrlSubject = urlSubject;

		int count = 1;

		while (_currentUrlSubjects.contains(uniqueUrlSubject)) {
			uniqueUrlSubject = urlSubject + StringPool.DASH + count;

			count++;
		}

		_currentUrlSubjects.add(uniqueUrlSubject);

		return uniqueUrlSubject;
	}

	private String _getUrlSubject(long id, String subject) {
		if (subject == null) {
			return String.valueOf(id);
		}

		subject = StringUtil.toLowerCase(subject.trim());

		if (Validator.isNull(subject) || Validator.isNumber(subject) ||
			subject.equals("rss")) {

			subject = String.valueOf(id);
		}
		else {
			subject = FriendlyURLNormalizerUtil.normalizeWithPeriodsAndSlashes(
				subject);
		}

		return subject.substring(0, Math.min(subject.length(), 254));
	}

	private void _populateCurrentUrlSubjects(Connection con)
		throws SQLException {

		try (PreparedStatement ps = con.prepareStatement(
				StringBundler.concat(
					"select subject from MBMessage where ",
					"(!MBMessage.urlSubject is null) or ",
					"(!MBMessage.urlSubject = '')"))) {

			try (ResultSet rs = ps.executeQuery()) {
				Set<String> urlSubjects = new HashSet<>();

				while (rs.next()) {
					String subject = rs.getString(1);

					urlSubjects.add(subject);
				}
			}
		}
	}

	private void _populateUrlSubject() throws SQLException {

		_populateCurrentUrlSubjects(connection);

		try (PreparedStatement ps1 = connection.prepareStatement(
				"select messageId, subject from MBMessage where (urlSubject " +
					"is null) or (urlSubject = '')");
			ResultSet rs = ps1.executeQuery();
			PreparedStatement ps2 = AutoBatchPreparedStatementUtil.autoBatch(
				connection.prepareStatement(
					"update MBMessage set urlSubject = ? where messageId = " +
						"?"))) {

			Map<Long, String> urlSubjects = new HashMap<>();

			while (rs.next()) {
				long messageId = rs.getLong(1);
				String subject = rs.getString(2);

				urlSubjects.put(
					messageId, _findUniqueUrlSubject(messageId, subject));

				String urlSubject = _getUrlSubject(messageId, subject);

				String uniqueUrlSubject = _findUniqueUrlSubject(
					messageId, urlSubject);

				ps2.setString(1, uniqueUrlSubject);

				ps2.setLong(2, messageId);

				ps2.addBatch();
			}

			ps2.executeBatch();
		}
	}


	private final Set<String> _currentUrlSubjects = new HashSet<>();

}