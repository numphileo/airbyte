"""
MIT License
Copyright (c) 2020 Airbyte
Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:
The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.
THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
"""
from functools import partial
from typing import Tuple, Mapping

from base_python import BaseClient

from grnhse import Harvest

DEFAULT_ITEMS_PER_PAGE = 100


def paginator(request, **params):
    """ Split requests in multiple batches and return records as generator
    """
    rows = request.get(**params)
    yield from rows
    while request.record_remaining:
        rows = request.get_next()
        yield from rows


class Client(BaseClient):
    ENTITIES = [
        'applications',
        'candidates',
        'close_reasons',
        'degrees',
        'departments',
        'job_posts',
        'jobs',
        'offers',
        'scorecards',
        'users',
        'custom_fields',
    ]

    def __init__(self, apikey):
        self._client = Harvest(api_key=apikey)
        # FIXME: patch missing endpoint
        self._client._uris['direct']['custom_fields'] = {
            'list': 'custom_fields',
            'retrieve': 'custom_field/{id}'
        }
        super().__init__()

    def list(self, name, **kwargs):
        yield from paginator(getattr(self.client, name), **kwargs)

    # def settings(self, **kwargs):
    #     url = 'settings/helpdesk'
    #     request = partial(self._client._get, url=url)
    #     return list(paginator(request, **kwargs))

    def _enumerate_methods(self) -> Mapping[str, callable]:
        return {
            entity: partial(self.list, name=entity)
            for entity in self.ENTITIES
        }

    def health_check(self) -> Tuple[bool, str]:
        # TODO
        alive = True
        error_msg = None

        try:
            self.settings()
        except FreshdeskError as error:
            alive = False
            error_msg = str(error)

        return alive, error_msg