/*
 * Copyright (c) 2018 Pavel Uvarov <pauknone@yahoo.com>
 *
 * This file is part of Ukase.
 *
 *  Ukase is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as
 *  published by the Free Software Foundation, either version 3 of the
 *  License, or (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program. If not, see <http://www.gnu.org/licenses/>.
 */


'use strict';

var angular = require('angular'),
    components = require('../../../components'),
    ngModule;


ngModule = angular
    .module('ukase.routes.home.index', [
        require('angular-ui-router'),
        components.ukaseData,
        components.ukasePdfView
    ]).config(homeIndexRoute);

require('./home-index.controller')(ngModule);

module.exports = ngModule.name;

require('./home-index.template.less');

function homeIndexRoute($stateProvider) {
    $stateProvider.state('home.index', {
        url: '/',
        template: require('./home-index.template.html')
    });
}
