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
import { List, AutoSizer, WindowScroller, CellMeasurer, CellMeasurerCache } from 'react-virtualized';
import SourceViewerLine from './SourceViewerLine';
import { translate } from '../../helpers/l10n';
import type { Duplication, SourceLine } from './types';
import type { Issue } from '../issue/types';

type Props = {
  displayAllIssues: boolean,
  duplications?: Array<Duplication>,
  duplicationsByLine: { [number]: Array<number> },
  duplicatedFiles?: Array<{ key: string }>,
  filterLine?: (SourceLine) => boolean,
  hasSourcesAfter: boolean,
  hasSourcesBefore: boolean,
  highlightedLine: number | null,
  highlightedSymbol: string | null,
  issues: Array<Issue>,
  issuesByLine: { [number]: Array<string> },
  issueLocationsByLine: { [number]: Array<{ from: number, to: number }> },
  issueSecondaryLocationsByIssueByLine: {
    [string]: {
      [number]: Array<{ from: number, to: number }>
    }
  },
  issueSecondaryLocationMessagesByIssueByLine: {
    [issueKey: string]: {
      [line: number]: Array<{ msg: string, index?: number }>
    }
  },
  loadDuplications: (SourceLine, HTMLElement) => void,
  loadSourcesAfter: () => void,
  loadSourcesBefore: () => void,
  loadingSourcesAfter: boolean,
  loadingSourcesBefore: boolean,
  onCoverageClick: (SourceLine, HTMLElement) => void,
  onDuplicationClick: (number, number) => void,
  onIssueSelect: (string) => void,
  onIssueUnselect: () => void,
  onLineClick: (number, HTMLElement) => void,
  onSCMClick: (SourceLine, HTMLElement) => void,
  onSymbolClick: (string) => void,
  selectedIssue: string | null,
  sources: Array<SourceLine>,
  symbolsByLine: { [number]: Array<string> }
};

const EMPTY_ARRAY = [];

// const ZERO_LINE = {
//   code: '',
//   duplicated: false,
//   line: 0
// };

export default class SourceViewerCode extends React.Component {
  props: Props;
  rowsCache: Object;

  constructor (props: Props) {
    super(props);
    this.rowsCache = new CellMeasurerCache({
      defaultHeight: 18,
      fixedWidth: true
    });
  }

  isSCMChanged (s: SourceLine, p: null | SourceLine) {
    let changed = true;
    if (p != null && s.scmAuthor != null && p.scmAuthor != null) {
      changed = (s.scmAuthor !== p.scmAuthor) || (s.scmDate !== p.scmDate);
    }
    return changed;
  }

  getDuplicationsForLine (line: SourceLine) {
    return this.props.duplicationsByLine[line.line] || EMPTY_ARRAY;
  }

  getIssuesForLine (line: SourceLine): Array<string> {
    return this.props.issuesByLine[line.line] || EMPTY_ARRAY;
  }

  getIssueLocationsForLine (line: SourceLine) {
    return this.props.issueLocationsByLine[line.line] || EMPTY_ARRAY;
  }

  getSecondaryIssueLocationsForLine (line: SourceLine, issueKey: string) {
    const index = this.props.issueSecondaryLocationsByIssueByLine;
    if (index[issueKey] == null) {
      return EMPTY_ARRAY;
    }
    return index[issueKey][line.line] || EMPTY_ARRAY;
  }

  getSecondaryIssueLocationMessagesForLine (line: SourceLine, issueKey: string) {
    return this.props.issueSecondaryLocationMessagesByIssueByLine[issueKey][line.line] || EMPTY_ARRAY;
  }

  renderLine = (options: { index: number, key: string, parent: Object, style: Object }) => {
    const hasCoverage = this.props.sources.some(s => s.coverageStatus != null);
    const hasDuplications = this.props.sources.some(s => s.duplicated);
    const displayFiltered = this.props.filterLine != null;
    const hasIssues = this.props.issues.length > 0;

    const line = this.props.sources[options.index];
    const { filterLine, selectedIssue, sources } = this.props;
    const filtered = filterLine ? filterLine(line) : null;
    const secondaryIssueLocations = selectedIssue ?
      this.getSecondaryIssueLocationsForLine(line, selectedIssue) : EMPTY_ARRAY;
    const secondaryIssueLocationMessages = selectedIssue ?
      this.getSecondaryIssueLocationMessagesForLine(line, selectedIssue) : EMPTY_ARRAY;

    const duplicationsCount = this.props.duplications ? this.props.duplications.length : 0;

    const issuesForLine = this.getIssuesForLine(line);

    // for the following properties pass null if the line for sure is not impacted
    const symbolsForLine = this.props.symbolsByLine[line.line] || [];
    const { highlightedSymbol } = this.props;
    const optimizedHighlightedSymbol = highlightedSymbol != null && symbolsForLine.includes(highlightedSymbol) ?
      highlightedSymbol : null;

    const optimizedSelectedIssue = selectedIssue != null && issuesForLine.includes(selectedIssue) ?
      selectedIssue : null;

    return (
      <CellMeasurer
        cache={this.rowsCache}
        columnIndex={0}
        key={options.key}
        parent={options.parent}
        rowIndex={options.index}>
        <div style={options.style}>
          <SourceViewerLine
            displayAllIssues={this.props.displayAllIssues}
            displayCoverage={hasCoverage}
            displayDuplications={hasDuplications}
            displayFiltered={displayFiltered}
            displayIssues={hasIssues}
            displaySCM={this.isSCMChanged(line, options.index > 0 ? sources[options.index - 1] : null)}
            duplications={this.getDuplicationsForLine(line)}
            duplicationsCount={duplicationsCount}
            filtered={filtered}
            highlighted={line.line === this.props.highlightedLine}
            highlightedSymbol={optimizedHighlightedSymbol}
            issueLocations={this.getIssueLocationsForLine(line)}
            issues={issuesForLine}
            line={line}
            loadDuplications={this.props.loadDuplications}
            onClick={this.props.onLineClick}
            onCoverageClick={this.props.onCoverageClick}
            onDuplicationClick={this.props.onDuplicationClick}
            onIssueSelect={this.props.onIssueSelect}
            onIssueUnselect={this.props.onIssueUnselect}
            onSCMClick={this.props.onSCMClick}
            onSymbolClick={this.props.onSymbolClick}
            secondaryIssueLocations={secondaryIssueLocations}
            secondaryIssueLocationMessages={secondaryIssueLocationMessages}
            selectedIssue={optimizedSelectedIssue}/>
          </div>
        </CellMeasurer>
    );
  };

  render () {
    const { sources } = this.props;

    // const hasFileIssues = hasIssues && this.props.issues.some(issue => !issue.line);

    return (
      <div>
        {this.props.hasSourcesBefore && (
          <div className="source-viewer-more-code">
            {this.props.loadingSourcesBefore ? (
                <div className="js-component-viewer-loading-before">
                  <i className="spinner"/>
                  <span className="note spacer-left">{translate('source_viewer.loading_more_code')}</span>
                </div>
              ) : (
                <button className="js-component-viewer-source-before" onClick={this.props.loadSourcesBefore}>
                  {translate('source_viewer.load_more_code')}
                </button>
              )}
          </div>
        )}

        <WindowScroller>
          {windowSize => (
            <AutoSizer disableHeight={true}>
              {size => (
                <List
                  autoHeight={true}
                  className="source-table"
                  deferredMeasurementCache={this.rowsCache}
                  height={windowSize.height}
                  overscanRowCount={50}
                  rowCount={sources.length}
                  rowHeight={this.rowsCache.rowHeight}
                  rowRenderer={this.renderLine}
                  scrollTop={windowSize.scrollTop}
                  width={size.width}
                  {...this.props}/>
              )}
            </AutoSizer>
          )}
        </WindowScroller>

        {this.props.hasSourcesAfter && (
          <div className="source-viewer-more-code">
            {this.props.loadingSourcesAfter ? (
                <div className="js-component-viewer-loading-after">
                  <i className="spinner"/>
                  <span className="note spacer-left">{translate('source_viewer.loading_more_code')}</span>
                </div>
              ) : (
                <button className="js-component-viewer-source-after" onClick={this.props.loadSourcesAfter}>
                  {translate('source_viewer.load_more_code')}
                </button>
              )}
          </div>
        )}
      </div>
    );
  }
}
