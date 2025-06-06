import requests

import requests
from requests.cookies import cookiejar_from_dict

question_url = "https://www.zhihu.com/api/v4/questions/1913903199322108502/feeds?include=data[*].is_normal,admin_closed_comment,reward_info,is_collapsed,annotation_action,annotation_detail,collapse_reason,is_sticky,collapsed_by,suggest_edit,comment_count,can_comment,content,editable_content,attachment,voteup_count,reshipment_settings,comment_permission,created_time,updated_time,review_info,relevant_info,question,excerpt,is_labeled,paid_info,paid_info_content,reaction_instruction,relationship.is_authorized,is_author,voting,is_thanked,is_nothelp;data[*].author.follower_count,vip_info,kvip_info,badge[*].topics;data[*].settings.table_of_content.enabled&offset=&limit=3&order=default&ws_qiangzhisafe=0&platform=desktop"
feed_url = "https://www.zhihu.com/api/v3/feed/topstory/recommend?action=down&ad_interval=-10&after_id=5&desktop=true&end_offset=5&page_number=2&session_token=xxxxx"
# question_url = "https://www.zhihu.com/api/v4/questions/653350805/feeds?cursor=b7a294c0db6d6decb4cc8050d5d24c9c&include=&limit=3&offset=1&order=default&platform=desktop&session_id=1749365187745647416&ws_qiangzhisafe=0"

headers = {
    "User-Agent": '''Mozilla/5.0 (X11; Linux x86_64; rv:128.0) Gecko/20100101 Firefox/128.0''',
    "Accept": "*/*",
    "Accept-Language": "en-US,en;q=0.5",
    "Accept-Encoding": "gzip, deflate, br, zstd",
    "Referer": "https://www.zhihu.com/question/1913903199322108502/answer/1914058776052474620",
    "x-requested-with": "fetch",
    "x-zse-93": "101_3_3.0",
    "x-zse-96": "2.0_Y42mJL44M=esPJeRMicG/Wqq3KNPR9RCSn=HS7+R=MncBQfN/581akrbyJr7gZRz",
    "Connection": "keep-alive",
    "Cookie": "Cookie",
    "Sec-Fetch-Dest": "empty",
    "Sec-Fetch-Mode": "cors",
    "Sec-Fetch-Site": "same-origin",
    "Priority": "u=4",
    "TE": "trailers"
}

# response = requests.get(question_url, headers=headers)



pass
