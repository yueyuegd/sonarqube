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
// @flow
import React from 'react';
import { connect } from 'react-redux';
import Helmet from 'react-helmet';
import PageHeaderContainer from '../../projects/components/PageHeaderContainer';
import FavoriteProjectsContainer from '../../projects/components/FavoriteProjectsContainer';
import { getOrganizationByKey } from '../../../store/rootReducer';
import { updateOrganization } from '../actions';
import { translate } from '../../../helpers/l10n';

class OrganizationFavoriteProjects extends React.Component {
  props: {
    children: Object,
    location: Object,
    organization: {
      key: string
    }
  };

  componentDidMount() {
    document.querySelector('html').classList.add('dashboard-page');
  }

  componentWillUnmount() {
    document.querySelector('html').classList.remove('dashboard-page');
  }

  render() {
    return (
      <div id="projects-page" className="page page-limited">
        <Helmet title={translate('projects.page')} titleTemplate="%s - SonarQube" />
        <PageHeaderContainer organization={this.props.organization} />
        <FavoriteProjectsContainer
          location={this.props.location}
          organization={this.props.organization}
        />
      </div>
    );
  }
}

const mapStateToProps = (state, ownProps) => ({
  organization: getOrganizationByKey(state, ownProps.params.organizationKey)
});

const mapDispatchToProps = { updateOrganization };

export default connect(mapStateToProps, mapDispatchToProps)(OrganizationFavoriteProjects);
