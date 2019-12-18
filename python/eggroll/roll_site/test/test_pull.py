#
#  Copyright 2019 The Eggroll Authors. All Rights Reserved.
#
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.
#
import unittest
from eggroll.roll_site.roll_site import RollSiteContext
from eggroll.core.session import ErSession
from eggroll.roll_pair.roll_pair import RollPairContext

class TestGet(unittest.TestCase):
    def test_get(self):
        rp_session = ErSession(session_id='testing', options={"eggroll.deploy.mode": "standalone"})
        rp_context = RollPairContext(rp_session)

        options = {'runtime_conf_path': 'python/eggroll/roll_site/conf/role_conf.json',
                   'server_conf_path': 'python/eggroll/roll_site/conf/server_conf.json',
                   'transfer_conf_path': 'python/eggroll/roll_site/conf/transfer_conf.json'}
        context = RollSiteContext("atest", options=options, rp_ctx=rp_context)

        _tag = "Hello"

        rs = context.load(name="RsaIntersectTransferVariable.rsa_pubkey", tag="{}".format(_tag))
        futures = rs.pull()
        print("result:", type(futures.result()))
        for future in futures.result():
          print("result:", type(future.result()))
          future.get()


