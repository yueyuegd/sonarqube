/*
 * SonarQube
 * Copyright (C) 2009-2017 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package it.organization;

import com.sonar.orchestrator.Orchestrator;
import it.Category3Suite;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.sonarqube.ws.client.HttpException;
import org.sonarqube.ws.client.WsClient;
import org.sonarqube.ws.client.organization.CreateWsRequest;
import org.sonarqube.ws.client.permission.AddUserWsRequest;
import util.user.UserRule;

import static java.lang.String.format;
import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static util.ItUtils.newAdminWsClient;

public class MembershipTest {

  private final static String ORGANIZATION_KEY = "organization-key";

  @ClassRule
  public static Orchestrator orchestrator = Category3Suite.ORCHESTRATOR;

  @ClassRule
  public static UserRule userRule = UserRule.from(orchestrator);

  @Rule
  public ExpectedException expectedException = ExpectedException.none();

  private static WsClient adminClient;

  @Before
  public void setUp() throws Exception {
    adminClient = newAdminWsClient(orchestrator);
    orchestrator.resetData();
    userRule.resetUsers();

    orchestrator.getServer().post("api/organizations/enable_support", emptyMap());
    createOrganization(ORGANIZATION_KEY);
  }

  @After
  public void tearDown() throws Exception {
    adminClient.organizations().delete(ORGANIZATION_KEY);
  }

  @Test
  public void add_and_remove_member() throws Exception {
    userRule.createUser("test", "test");
    adminClient.organizations().addMember(ORGANIZATION_KEY, "test");
    verifyMembership("test", ORGANIZATION_KEY, true);

    adminClient.organizations().removeMember(ORGANIZATION_KEY, "test");
    verifyMembership("test", ORGANIZATION_KEY, false);
  }

  @Test
  public void remove_organization_admin_member() throws Exception {
    userRule.createUser("test", "test");
    adminClient.organizations().addMember(ORGANIZATION_KEY, "test");
    adminClient.permissions().addUser(new AddUserWsRequest().setLogin("test").setPermission("admin").setOrganization(ORGANIZATION_KEY));
    verifyMembership("test", ORGANIZATION_KEY, true);

    adminClient.organizations().removeMember(ORGANIZATION_KEY, "test");
    verifyMembership("test", ORGANIZATION_KEY, false);
  }

  @Test
  public void fail_to_remove_organization_admin_member_when_last_admin() throws Exception {
    userRule.createUser("test", "test");
    adminClient.organizations().addMember(ORGANIZATION_KEY, "test");
    adminClient.permissions().addUser(new AddUserWsRequest().setLogin("test").setPermission("admin").setOrganization(ORGANIZATION_KEY));
    verifyMembership("test", ORGANIZATION_KEY, true);
    // Admin is the creator of the organization so he was granted with admin permission
    adminClient.organizations().removeMember(ORGANIZATION_KEY, "admin");

    expectedException.expect(HttpException.class);
    expectedException.expectMessage("The last administrator member cannot be removed");

    adminClient.organizations().removeMember(ORGANIZATION_KEY, "test");
  }

  @Test
  public void remove_user_remove_its_membership() throws Exception {
    userRule.createUser("test", "test");
    adminClient.organizations().addMember(ORGANIZATION_KEY, "test");
    verifyMembership("test", ORGANIZATION_KEY, true);

    userRule.deactivateUsers("test");
    verifyMembership("test", ORGANIZATION_KEY, false);
  }

  private void verifyMembership(String login, String organizationKey, boolean isMember) {
    // TODO replace with search member WS
    int count = orchestrator.getDatabase().countSql(format("SELECT COUNT(1) FROM organization_members om " +
      "INNER JOIN users u ON u.id=om.user_id AND u.login='%s' " +
      "INNER JOIN organizations o ON o.uuid=om.organization_uuid AND o.kee='%s'", login, organizationKey));
    assertThat(count).isEqualTo(isMember ? 1 : 0);
  }

  private static void createOrganization(String organizationKey) {
    adminClient.organizations().create(new CreateWsRequest.Builder().setKey(organizationKey).setName(organizationKey).build()).getOrganization();
  }

}
