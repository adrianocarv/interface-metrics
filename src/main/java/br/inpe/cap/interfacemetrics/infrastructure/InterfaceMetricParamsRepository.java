package br.inpe.cap.interfacemetrics.infrastructure;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import br.inpe.cap.interfacemetrics.domain.InterfaceMetric;

public class InterfaceMetricParamsRepository extends BaseRepository {

	private String table = "interface_metrics_params";

	public InterfaceMetricParamsRepository(RepositoryType repositoryType) {
		table += repositoryType.getSufix();
	}

	public void deleteTable() throws Exception {
		String sql = "DELETE from " + table;

		Connection conn = getConnection();
		Statement stmt = conn.createStatement();
		stmt.executeUpdate(sql);

		stmt.close();
		conn.close();
	}

	public void deleteParams(InterfaceMetric interfaceMetric) throws Exception {
		String sql = "DELETE from " + table + " where interface_metrics_id = " + interfaceMetric.getId();

		Connection conn = getConnection();
		Statement stmt = conn.createStatement();
		stmt.executeUpdate(sql);

		stmt.close();
		conn.close();
	}

	public void insertParams(InterfaceMetric interfaceMetric) throws Exception {
		Connection conn = getConnection();
		PreparedStatement ps = null;

		for (String param : interfaceMetric.getParamsNames()) {
			String sql = "INSERT into " + table + " (interface_metrics_id, param) values (?, ?)";
			ps = conn.prepareStatement(sql);
			ps.setLong(1, interfaceMetric.getId());
			ps.setString(2, param);
			ps.executeUpdate();
		}

		if (ps != null)
			ps.close();
		conn.close();
	}

	public List<String> getParams(InterfaceMetric interfaceMetric) throws Exception {
		Connection conn = getConnection();
		Statement stmt = conn.createStatement();

		String sql = "SELECT * FROM " + table + " where interface_metrics_id = " + interfaceMetric.getId();
		ResultSet rs = stmt.executeQuery(sql);

		List<String> params = new ArrayList<String>();

		while (rs.next()) {
			String param = rs.getString("param");
			params.add(param);
		}

		stmt.close();
		conn.close();

		return params;
	}

	public int countAll() throws Exception {
		Connection conn = getConnection();
		Statement stmt = conn.createStatement();

		String sql = "SELECT count(*) as total FROM " + table;
		ResultSet rs = stmt.executeQuery(sql);

		int total = 0;
		while (rs.next()) {
			total = rs.getInt("total");
		}

		stmt.close();
		conn.close();

		return total;
	}

}
