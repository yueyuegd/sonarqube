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

package org.sonar.server.organization.ws;

import com.google.common.base.Throwables;
import java.io.IOException;
import java.util.stream.IntStream;
import org.junit.Rule;
import org.junit.Test;
import org.sonar.api.config.MapSettings;
import org.sonar.api.server.ws.WebService;
import org.sonar.api.server.ws.WebService.Param;
import org.sonar.db.DbClient;
import org.sonar.db.DbTester;
import org.sonar.db.organization.OrganizationDto;
import org.sonar.db.user.UserDto;
import org.sonar.server.es.EsTester;
import org.sonar.server.organization.DefaultOrganizationProvider;
import org.sonar.server.organization.TestDefaultOrganizationProvider;
import org.sonar.server.user.index.UserIndex;
import org.sonar.server.user.index.UserIndexDefinition;
import org.sonar.server.user.index.UserIndexer;
import org.sonar.server.ws.TestRequest;
import org.sonar.server.ws.WsActionTester;
import org.sonarqube.ws.Common.Paging;
import org.sonarqube.ws.MediaTypes;
import org.sonarqube.ws.Organizations.SearchMembersWsResponse;
import org.sonarqube.ws.Organizations.User;
import org.sonarqube.ws.client.organization.SearchMembersWsRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.sonar.core.util.Protobuf.setNullable;

public class SearchMembersActionTest {

  @Rule
  public EsTester es = new EsTester(new UserIndexDefinition(new MapSettings()));

  @Rule
  public DbTester db = DbTester.create();
  private DbClient dbClient = db.getDbClient();

  private DefaultOrganizationProvider organizationProvider = TestDefaultOrganizationProvider.from(db);
  private UserIndexer indexer = new UserIndexer(dbClient, es.client());

  private WsActionTester ws = new WsActionTester(new SearchMembersAction(dbClient, new UserIndex(es.client()), organizationProvider));

  private SearchMembersWsRequest request = new SearchMembersWsRequest();

  @Test
  public void empty_response() {
    SearchMembersWsResponse result = call();

    assertThat(result.getUsersList()).isEmpty();
    assertThat(result.getPaging())
      .extracting(Paging::getPageIndex, Paging::getPageSize, Paging::getTotal)
      .containsExactly(1, 50, 0);
  }

  @Test
  public void search_members_of_default_organization() {
    OrganizationDto defaultOrganization = db.getDefaultOrganization();
    OrganizationDto anotherOrganization = db.organizations().insert();
    UserDto user = insertUser();
    UserDto anotherUser = insertUser();
    UserDto userInAnotherOrganization = insertUser();
    db.organizations().addMember(defaultOrganization, user);
    db.organizations().addMember(defaultOrganization, anotherUser);
    db.organizations().addMember(anotherOrganization, userInAnotherOrganization);

    SearchMembersWsResponse result = call();

    assertThat(result.getUsersList())
      .extracting(User::getLogin, User::getName)
      .containsOnly(
        tuple(user.getLogin(), user.getName()),
        tuple(anotherUser.getLogin(), anotherUser.getName()));
  }

  @Test
  public void search_members_of_specified_organization() {
    OrganizationDto organization = db.organizations().insert();
    OrganizationDto anotherOrganization = db.organizations().insert();
    UserDto user = insertUser();
    UserDto anotherUser = insertUser();
    UserDto userInAnotherOrganization = insertUser();
    db.organizations().addMember(organization, user);
    db.organizations().addMember(organization, anotherUser);
    db.organizations().addMember(anotherOrganization, userInAnotherOrganization);
    request.setOrganization(organization.getKey());

    SearchMembersWsResponse result = call();

    assertThat(result.getUsersList())
      .extracting(User::getLogin, User::getName)
      .containsOnly(
        tuple(user.getLogin(), user.getName()),
        tuple(anotherUser.getLogin(), anotherUser.getName()));
  }

  @Test
  public void search_non_members() {
    OrganizationDto defaultOrganization = db.getDefaultOrganization();
    OrganizationDto anotherOrganization = db.organizations().insert();
    UserDto user = insertUser();
    UserDto anotherUser = insertUser();
    UserDto userInAnotherOrganization = insertUser();
    db.organizations().addMember(anotherOrganization, user);
    db.organizations().addMember(anotherOrganization, anotherUser);
    db.organizations().addMember(defaultOrganization, userInAnotherOrganization);
    request.setSelected(WebService.SelectionMode.DESELECTED.value());

    SearchMembersWsResponse result = call();

    assertThat(result.getUsersList())
      .extracting(User::getLogin, User::getName)
      .containsOnly(
        tuple(user.getLogin(), user.getName()),
        tuple(anotherUser.getLogin(), anotherUser.getName()));
  }

  @Test
  public void search_members_pagination() {
    IntStream.range(0, 10).forEach(i -> {
      UserDto userDto = db.users().insertUser(user -> user.setName("USER_" + i));
      db.organizations().addMember(db.getDefaultOrganization(), userDto);
      indexer.index(userDto.getLogin());
    });
    request.setPage(2).setPageSize(3);

    SearchMembersWsResponse result = call();

    assertThat(result.getUsersList()).extracting(User::getName)
      .containsExactly("USER_3", "USER_4", "USER_5");
    assertThat(result.getPaging())
      .extracting(Paging::getPageIndex, Paging::getPageSize, Paging::getTotal)
      .containsExactly(2, 3, 10);
  }

  @Test
  public void search_members_by_name() {
    IntStream.range(0, 10).forEach(i -> {
      UserDto userDto = db.users().insertUser(user -> user.setName("USER_" + i));
      db.organizations().addMember(db.getDefaultOrganization(), userDto);
      indexer.index(userDto.getLogin());
    });
    request.setQuery("_9");

    SearchMembersWsResponse result = call();

    assertThat(result.getUsersList()).extracting(User::getName).containsExactly("USER_9");
  }

  @Test
  public void search_members_by_login() {
    IntStream.range(0, 10).forEach(i -> {
      UserDto userDto = db.users().insertUser(user -> user.setLogin("USER_" + i));
      db.organizations().addMember(db.getDefaultOrganization(), userDto);
      indexer.index(userDto.getLogin());
    });
    request.setQuery("_9");

    SearchMembersWsResponse result = call();

    assertThat(result.getUsersList()).extracting(User::getLogin).containsExactly("USER_9");
  }

  @Test
  public void search_members_by_email() {
    IntStream.range(0, 10).forEach(i -> {
      UserDto userDto = db.users().insertUser(user -> user
        .setLogin("L" + i)
        .setEmail("USER_" + i + "@email.com"));
      db.organizations().addMember(db.getDefaultOrganization(), userDto);
      indexer.index(userDto.getLogin());
    });
    request.setQuery("_9");

    SearchMembersWsResponse result = call();

    assertThat(result.getUsersList()).extracting(User::getLogin).containsExactly("L9");
  }

  @Test
  public void definition() {
    WebService.Action action = ws.getDef();

    assertThat(action.key()).isEqualTo("search_members");
    assertThat(action.params()).extracting(Param::key)
      .containsOnly("q", "selected", "p", "ps", "organization");
    assertThat(action.responseExampleAsString()).isNotEmpty();
    assertThat(action.since()).isEqualTo("6.4");
    assertThat(action.isInternal()).isTrue();
    assertThat(action.isPost()).isFalse();
    assertThat(action.param("organization").isInternal()).isTrue();
    assertThat(action.param("selected").possibleValues()).containsOnly("selected", "deselected");
  }

  private UserDto insertUser() {
    UserDto userDto = db.users().insertUser();
    indexer.index(userDto.getLogin());

    return userDto;
  }

  private SearchMembersWsResponse call() {
    TestRequest wsRequest = ws.newRequest()
      .setMediaType(MediaTypes.PROTOBUF);
    setNullable(request.getOrganization(), o -> wsRequest.setParam("organization", o));
    setNullable(request.getQuery(), q -> wsRequest.setParam(Param.TEXT_QUERY, q));
    setNullable(request.getPage(), p -> wsRequest.setParam(Param.PAGE, String.valueOf(p)));
    setNullable(request.getPageSize(), ps -> wsRequest.setParam(Param.PAGE_SIZE, String.valueOf(ps)));
    setNullable(request.getSelected(), s -> wsRequest.setParam(Param.SELECTED, s));

    try {
      return SearchMembersWsResponse.parseFrom(wsRequest.execute().getInputStream());
    } catch (IOException e) {
      throw Throwables.propagate(e);
    }
  }
}
