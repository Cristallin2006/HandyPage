# 源验证报告(calibre recipes)

- 生成日期:2026-07-20
- 候选范围:calibre 内置 1097 个 recipes 中的英文期刊类源,共 **483** 个
- 测试方法:对每个源的主链接(RSS feed 或首页)发起 HTTP GET,浏览器 UA,超时 8s
- **无代理口径:强制直连(ProxyHandler 置空),不受系统代理影响**
- **代理口径:HTTP 代理 127.0.0.1:7890(FlClash);明细表中延迟带 * 者为代理下测得**
- 判定:✅ 2xx/3xx;⚠️ 401/403/429=可达但被反爬或付费墙拒绝;❌ 超时/重置/DNS=不可达

## 一、两阶段连通性对比

| 判定 | 无代理 | 代理 |
|---|---|---|
| ✅ 可用 | 153 | 341 |
| ⚠️ 可达被拒 | 59 | 56 |
| ❌ 404 | 5 | 12 |
| ❌ 不可达 | 212 | 16 |
| ➖ 未测试 | 54 | 54 |
| **代理复活(不可达/被拒→可用)** | - | **191** |
| 无法静态提取 URL(未测) | 54 | |

## 二、代理复活源清单(共 191 个,⭐=学习者推荐)

| 源 ID | 名称 | 类型 | 代理延迟 | 主链接 |
|---|---|---|---|---|
| `abc_au` | ABC News ⭐ | RSS(低维护) | 1094ms | https://www.abc.net.au/news/feed/45910/rss.xml |
| `al_jazeera` | Al Jazeera in English ⭐ | RSS(低维护) | 703ms | http://www.aljazeera.com/xml/rss/all.xml |
| `bbc` | BBC News ⭐ | RSS(低维护) | 1733ms | https://feeds.bbci.co.uk/news/uk/rss.xml |
| `bbc_fast` | BBC News (fast) ⭐ | RSS(低维护) | 1843ms | https://feeds.bbci.co.uk/news/science_and_environment/rss.xml |
| `cbc_canada` | CBC Canada ⭐ | RSS(低维护) | 6000ms | https://www.cbc.ca/webfeed/rss/rss-topstories |
| `cnn` | CNN ⭐ | RSS(低维护) | 4813ms | http://rss.cnn.com/rss/cnn_topstories.rss |
| `financial_times` | Financial Times ⭐ | RSS(低维护) | 5983ms | https://www.ft.com/firstft?format=rss |
| `guardian` | The Guardian and The Observer ⭐ | 自定义解析(高维护) | 5578ms | https://www.theguardian.com/uk/sport |
| `hindufeeds` | The Hindu News ⭐ | RSS(低维护) | 4985ms | https://www.thehindu.com/opinion/feeder/default.rss |
| `independent` | The Independent ⭐ | RSS(低维护) | 5422ms | http://www.independent.co.uk/news/uk/rss |
| `japan_times` | The Japan Times ⭐ | RSS(低维护) | 5671ms | https://www.japantimes.co.jp/feed/topstories/ |
| `scmp` | South China Morning Post ⭐ | RSS(低维护) | 6468ms | https://www.scmp.com/rss/2/feed |
| `straitstimes` | The Straits Times ⭐ | RSS(低维护) | 6484ms | https://www.straitstimes.com/news/world/rss.xml |
| `afr` | Australian Financial Review | 自定义解析(高维护) | 1969ms | https://www.afr.com/ |
| `albert_mohler` | Albert Mohler's Blog | RSS(低维护) | 2110ms | http://feeds.feedburner.com/AlbertMohlersBlog?format=xml |
| `american_thinker` | American Thinker | RSS(低维护) | 1797ms | http://feeds.feedburner.com/americanthinker |
| `amspec` | The American Spectator | 自定义解析(高维护) | 3516ms | http://spectator.org/issues/current |
| `android_police` | Android Police | RSS(低维护) | 1327ms | https://www.androidpolice.com/feed/phones/ |
| `ars_technica` | Ars Technica | RSS(低维护) | 6155ms | https://feeds.arstechnica.com/arstechnica/index |
| `asianreviewofbooks` | The Asian Review of Books | RSS(低维护) | 8983ms | http://asianreviewofbooks.com/content/feed/ |
| `backyard_boss` | Backyard Boss | RSS(低维护) | 6827ms | https://www.backyardboss.net/feed/category/gardening/ |
| `baltimore_sun` | The Baltimore Sun | RSS(低维护) | 1718ms | http://feeds.feedburner.com/baltimoresun/news/rss2 |
| `bbc_sport` | BBC Sport | RSS(低维护) | 4577ms | http://newsrss.bbc.co.uk/rss/sportonline_uk_edition/front_page/rss.xml |
| `bellingcat_en` | Bellingcat | RSS(低维护) | 1343ms | https://www.bellingcat.com/feed/ |
| `biamag_en` | Bianet-English | RSS(低维护) | 3750ms | http://www.bianet.org/english.rss |
| `big_picture` | The Big Picture | RSS(低维护) | 1765ms | http://feeds.feedburner.com/TheBigPicture |
| `biggovernment` | Big Government | RSS(低维护) | 1360ms | http://feeds.feedburner.com/breitbart |
| `bloomberg` | Bloomberg | 自定义解析(高维护) | 921ms | https://cdn-mobapi.bloomberg.com |
| `bloomberg-business-week` | Bloomberg Businessweek | 自定义解析(高维护) | 5406ms | https://cdn-mobapi.bloomberg.com |
| `business_insider` | Business Insider | RSS(低维护) | 688ms | http://feeds2.feedburner.com/businessinsider |
| `car_buzz` | Car Buzz | RSS(低维护) | 6313ms | https://carbuzz.com/feed/category/news/ |
| `catholic_news_agency` | Catholic News Agency | RSS(低维护) | 969ms | http://feeds.feedburner.com/catholicnewsagency/dailynews-us |
| `cato` | The CATO Institute | RSS(低维护) | 10250ms | http://feeds.cato.org/CatoRecentOpeds |
| `cbn` | CBN News | RSS(低维护) | 15561ms | http://www.cbn.com/cbnnews/world/feed/ |
| `chicago_tribune` | Chicago Tribune | 自定义解析(高维护) | 17655ms | https://www.chicagotribune.com/ |
| `chr_mon` | The Christian Science Monitor - daily | RSS(低维护) | 6859ms | http://rss.csmonitor.com/feeds/usa |
| `coda` | Coda | RSS(低维护) | 16733ms | https://www.codastory.com/feed/ |
| `collider` | Collider | RSS(低维护) | 6188ms | https://collider.com/feed/category/movies/ |
| `comic_book_archive` | Comic Book Archive | RSS(低维护) | 6468ms | https://www.cbr.com/feed/tag/marvel/ |
| `computer_weekly` | ComputerWeekly | RSS(低维护) | 7453ms | https://www.computerweekly.com/rss/IT-careers-and-IT-skills.xml |
| `cracked_com` | Cracked.com | RSS(低维护) | 1000ms | http://feeds.feedburner.com/CrackedRSS/ |
| `crikey` | Crikey | RSS(低维护) | 6000ms | https://www.crikey.com.au/politics/feed |
| `daily_mail` | The Daily Mail | RSS(低维护) | 11906ms | https://www.dailymail.co.uk/home/index.rss |
| `daily_writing_tips` | Daily Writing Tips | RSS(低维护) | 718ms | http://feeds2.feedburner.com/DailyWritingTips |
| `dailyreckoning` | The Daily Reckoning - US edition | RSS(低维护) | 875ms | http://feeds.feedburner.com/dailyreckoning?format=xml |
| `dark_horizons` | Dark Horizons | RSS(低维护) | 9110ms | http://www.darkhorizons.com/feeds/news.atom |
| `deccan_herald` | Deccan Herald | 自定义解析(高维护) | 5000ms | https://www.deccanherald.com/ |
| `democracy_now` | Democracy now! | RSS(低维护) | 17906ms | http://www.democracynow.org/democracynow.rss |
| `desiring_god` | Desiring God | RSS(低维护) | 1030ms | http://feeds.feedburner.com/DGBlog?format=xml |
| `deutsche_welle_en` | Deutsche Welle | RSS(低维护) | 6484ms | http://rss.dw.de/rdf/rss-en-world |
| `dot_net` | Creative Blog | RSS(低维护) | 6156ms | http://www.creativebloq.com/feed/ |
| `dual_shockers` | Dual SHOCKERS | RSS(低维护) | 5625ms | https://www.dualshockers.com/feed/video-game-news/ |
| `en_globes_co_il` | Globes in English | RSS(低维护) | 5905ms | https://www.globes.co.il/WebService/Rss/RssFeeder.asmx/FeederNode?iID=942 |
| `epl_talk` | EPL Talk | RSS(低维护) | 734ms | http://feeds.feedburner.com/EPLTalk |
| `epw` | Economic and Political Weekly | 自定义解析(高维护) | 6734ms | http://www.epw.in/ |
| `epw_magazine` | EPW Magazine | 自定义解析(高维护) | 6046ms | https://www.epw.in/journal/epw-archive |
| `equestria_daily` | Equestria Daily | 自定义解析(高维护) | 6015ms | https://www.equestriadaily.com/search/label/ |
| `espn` | ESPN | RSS(低维护) | 6047ms | https://www.espn.com/espn/rss/news |
| `fairbanks_daily` | Fairbanks Daily News-miner | RSS(低维护) | 7328ms | http://www.newsminer.com/search/?f=rss&t=article&c=news/alaska_news&l=50&s=start_time&sd=desc |
| `fan_graphs` | FanGraphs | RSS(低维护) | 1000ms | http://feeds.feedburner.com/FanGraphs?format=xml |
| `fastcompany` | Fast Company | RSS(低维护) | 781ms | http://feeds.feedburner.com/fastcompany/headlines |
| `financialsense` | Financial Sense | RSS(低维护) | 405ms | http://feeds.feedburner.com/fso |
| `first_things` | First Things | 自定义解析(高维护) | 11327ms | https://www.firstthings.com/ |
| `flickr` | Flickr Blog | RSS(低维护) | 656ms | http://feeds.feedburner.com/Flickrblog |
| `football_fancast` | Football Fancast | RSS(低维护) | 6422ms | https://www.footballfancast.com/feed/tag/euro-2024/ |
| `football_league_world` | Football League World | RSS(低维护) | 3485ms | https://footballleagueworld.co.uk/feed/transfer-rumours/ |
| `foreign_policy` | Foreign Policy | 自定义解析(高维护) | 5750ms | https://foreignpolicy.com/the-magazine |
| `foreignaffairs` | Foreign Affairs | 自定义解析(高维护) | 2438ms | https://www.foreignaffairs.com/issues/ |
| `frontline` | Frontline | 自定义解析(高维护) | 5250ms | https://frontline.thehindu.com/current-issue/ |
| `futurismic` | Futurismic | RSS(低维护) | 640ms | http://feeds2.feedburner.com/futurismic_feed |
| `game_rant` | Game Rant | RSS(低维护) | 5656ms | https://gamerant.com/feed/gaming/ |
| `give_me_sports` | Give Me Sports | RSS(低维护) | 5843ms | https://www.givemesport.com/feed/tag/premier-league/ |
| `gkt` | General Knowledge Today | 自定义解析(高维护) | 5218ms | https://www.gktoday.in/current-affairs/ |
| `glasgow_herald` | Glasgow Herald | RSS(低维护) | 5843ms | https://www.heraldscotland.com/news/rss/ |
| `glenn_greenwald` | Glenn Greenwald | guardian.co.uk | RSS(低维护) | 16922ms | http://www.guardian.co.uk/profile/glenn-greenwald/rss |
| `greatist` | Greatist | RSS(低维护) | 6093ms | https://greatist.com/feed |
| `greensboro_news_and_record` | Greensboro News & Record | RSS(低维护) | 6686ms | http://www.greensboro.com/search/?q=&t=article&l=10&d=&d1=&d2=&s=start_time&sd=desc&c[]=news,news/*&f=rss |
| `habr` | Habr | RSS(低维护) | 6827ms | https://habr.com/en/rss/news/?fl=en |
| `hamilton_spectator` | Hamilton Spectator | 自定义解析(高维护) | 1485ms | https://www.thespec.com/ |
| `hardcore_gamer` | Hardcore Gamer | RSS(低维护) | 1281ms | https://hardcoregamer.com/feed/category/news/ |
| `healthline` | Healthline | RSS(低维护) | 718ms | https://www.healthline.com/rss/health-news |
| `hindustan_times` | Hindustan Times | RSS(低维护) | 5094ms | https://www.hindustantimes.com/feeds/rss/editorials/rssfeed.xml |
| `hot_cars` | Hot Cars | RSS(低维护) | 6968ms | https://www.hotcars.com/feed/category/fast-cars/ |
| `hotair` | Hot Air | RSS(低维护) | 546ms | http://feeds.feedburner.com/hotair/main |
| `howtogeek` | How-To Geek | RSS(低维护) | 5875ms | https://www.howtogeek.com/feed/category/desktop/ |
| `independent_australia` | Independent Australia | RSS(低维护) | 688ms | https://feeds.feedburner.com/IndependentAustralia |
| `india_speaks_reddit` | IndiaSpeaksReddit | RSS(低维护) | 6061ms | https://www.reddit.com/r/IndiaSpeaks.rss |
| `india_today` | India Today Magazine | 自定义解析(高维护) | 5250ms | https://www.indiatoday.in/magazine |
| `indic_today` | Indic Today | RSS(低维护) | 5640ms | https://www.indica.today/feed/ |
| `inquirer_net` | Inquirer.net | RSS(低维护) | 5438ms | http://www.inquirer.net/fullfeed |
| `irish_independent` | Irish Independent | RSS(低维护) | 6156ms | http://www.independent.ie/rss |
| `jacobinmag` | Jacobin | 自定义解析(高维护) | 13328ms | https://www.jacobinmag.com/store/issues |
| `jakarta_post` | Jakarta Post | 自定义解析(高维护) | 5500ms | http://www.thejakartapost.com/ |
| `japan_news` | News On Japan | RSS(低维护) | 4983ms | http://newsonjapan.com/rss/top.xml |
| `kirkusreviews` | Kirkus Reviews | 自定义解析(高维护) | 6468ms | https://www.kirkusreviews.com/magazine/current/ |
| `krebs_on_security` | Krebs on Security | RSS(低维护) | 734ms | http://feeds.feedburner.com/KrebsOnSecurity |
| `lamebook` | Lamebook | RSS(低维护) | 890ms | http://feeds.feedburner.com/Lamebook |
| `le_monde_en` | Le Monde in English | RSS(低维护) | 5234ms | https://www.lemonde.fr/en/rss/une.xml |
| `livemint` | Live Mint | RSS(低维护) | 5077ms | https://www.livemint.com/rss/companies |
| `mainichi_en` | The Mainichi | 自定义解析(高维护) | 891ms | https://mainichi.jp/english/ |
| `make_use_of` | Make Use of | RSS(低维护) | 6719ms | https://www.makeuseof.com/feed/category/pc-mobile/ |
| `merco_press` | Merco Press | RSS(低维护) | 6452ms | http://en.mercopress.com/rss/antarctica |
| `michellemalkin` | Michelle Malkin | RSS(低维护) | 906ms | http://feeds.feedburner.com/michellemalkin/posts |
| `motherjones` | Mother Jones | RSS(低维护) | 1421ms | http://feeds.feedburner.com/motherjones/feed |
| `mwjournal` | Microwave Journal | 自定义解析(高维护) | 7327ms | http://www.microwavejournal.com |
| `national_post` | National Post | RSS(低维护) | 155ms | http://nationalpost.com/rss |
| `navy_times` | Army and Navy Times | RSS(低维护) | 6234ms | https://www.navytimes.com/arc/outboundfeeds/rss/?outputType=xml |
| `neowin` | Neowin.net | RSS(低维护) | 7827ms | http://www.neowin.net/news/rss/software |
| `new_york_review_of_books` | New York Review of Books | 自定义解析(高维护) | 6406ms | https://www.nybooks.com/current-issue |
| `new_york_review_of_books_no_sub` | New York Review of Books (no subscription) | 自定义解析(高维护) | 6436ms | https://www.nybooks.com/current-issue |
| `newrepublicmag` | The New Republic Magazine | 自定义解析(高维护) | 6500ms | https://newrepublic.com/api/content/magazine |
| `newsminute` | The News Minute | 自定义解析(高维护) | 1422ms | https://www.thenewsminute.com/ |
| `novinite` | Novinite.com | RSS(低维护) | 2000ms | http://www.novinite.com/services/news_rdf.php?category_id=1 |
| `nspm_int` | NSPM in English | RSS(低维护) | 6561ms | http://www.nspm.rs/nspm-in-english/feed/rss.html |
| `nypost` | New York Post | RSS(低维护) | 10250ms | https://nypost.com/us-news/feed/ |
| `nyt_magazine` | NYT Magazine | RSS(低维护) | 6936ms | https://rss.nytimes.com/services/xml/rss/nyt/Magazine.xml |
| `nyt_tmag` | NYT T Magazine | RSS(低维护) | 16766ms | https://rss.nytimes.com/services/xml/rss/nyt/tmagazine.xml |
| `nytfeeds` | NYT News | RSS(低维护) | 15906ms | https://rss.nytimes.com/services/xml/rss/nyt/Opinion.xml |
| `nytimes_cooking` | NY Times Cooking | 自定义解析(高维护) | 3219ms | https://cooking.nytimes.com/topics/what-to-cook-this-week |
| `nytimes_sports` | New York Times Sports Beat | RSS(低维护) | 6656ms | https://fifthdown.blogs.nytimes.com/feed/ |
| `nytimes_tech` | New York Times Technology Beat | RSS(低维护) | 16155ms | https://rss.nytimes.com/services/xml/rss/nyt/Technology.xml |
| `nytimesbook` | New York Times Book Review | 自定义解析(高维护) | 7186ms | https://www.nytimes.com/pages/books/review/index.html |
| `oc_register` | Orange County Register | RSS(低维护) | 2063ms | https://www.ocregister.com/news/ |
| `opindia` | opindia | RSS(低维护) | 1250ms | https://feeds.feedburner.com/opindia |
| `orfonline` | Observer Research Foundation | 自定义解析(高维护) | 5235ms | https://www.orfonline.org |
| `ourdailybread` | Our Daily Bread | RSS(低维护) | 14593ms | http://odb.org/feed/ |
| `outlook_india` | Outlook Magazine | 自定义解析(高维护) | 750ms | https://www.outlookindia.com/magazine/ |
| `pajama` | Pajamas Media | RSS(低维护) | 1031ms | http://feeds.feedburner.com/PajamasMedia |
| `parisreview` | The Paris Review Blog | RSS(低维护) | 609ms | http://feeds.feedburner.com/TheParisReviewBlog |
| `pc_world` | PCWorld | 自定义解析(高维护) | 4906ms | https://www.pcworld.com/ |
| `pocket_lint` | Pocket-lint | RSS(低维护) | 5625ms | https://www.pocket-lint.com/feed/devices-segment/ |
| `poliitico_eu` | Politico.eu | RSS(低维护) | 6609ms | https://www.politico.eu/section/policy/feed |
| `press_information_bureau` | Press Information Bureau | 自定义解析(高维护) | 3280ms | https://pib.gov.in/Allrel.aspx |
| `project_syndicate` | Project Syndicate | RSS(低维护) | 1734ms | https://www.project-syndicate.org/rss/section/economics |
| `psych` | Psychology Today | 自定义解析(高维护) | 6218ms | https://www.psychologytoday.com/us/magazine/archive |
| `rushisaband` | Rushisaband | RSS(低维护) | 688ms | http://feeds2.feedburner.com/rushisaband/blog |
| `science_based_medicine` | Science Based Medicine | RSS(低维护) | 7875ms | http://www.sciencebasedmedicine.org/?feed=rss2 |
| `scott_hanselman` | Scott Hanselman's Computer Zen | RSS(低维护) | 7108ms | http://feeds2.feedburner.com/ScottHanselman |
| `screen_rant` | Screen Rant | RSS(低维护) | 6422ms | https://screenrant.com/feed/movies/ |
| `seattle_times` | The Seattle Times | RSS(低维护) | 4858ms | https://www.seattletimes.com/seattle-news/feed/ |
| `skeptic` | The Skeptic | RSS(低维护) | 1093ms | http://feeds.feedburner.com/Skepticcom |
| `smh` | The Sydney Morning Herald | RSS(低维护) | 6718ms | https://www.smh.com.au/rss/feed.xml |
| `spectator-au` | Spectator Australia | RSS(低维护) | 3797ms | https://www.spectator.com.au/feed/ |
| `spectator_magazine` | Spectator Magazine | 自定义解析(高维护) | 10030ms | https://www.spectator.co.uk/magazine |
| `spiegel_int` | Spiegel Online International | RSS(低维护) | 5640ms | https://www.spiegel.de/international/world/index.rss |
| `sports_illustrated` | Sports Illustrated | 自定义解析(高维护) | 6406ms | https://www.si.com/ |
| `sportstar` | Sportstar | 自定义解析(高维护) | 7766ms | https://sportstar.thehindu.com/magazine/issue/vol |
| `staradvertiser` | Honolulu Star-Advertiser | RSS(低维护) | 686ms | http://www.staradvertiser.com/category/breaking-news/feed/ |
| `swarajya` | Swarajya Magazine | 自定义解析(高维护) | 5436ms | https://swarajyamag.com/all-issues |
| `techdirt` | Tech Dirt | RSS(低维护) | 671ms | http://feeds.feedburner.com/techdirt/feed |
| `tehelka` | Tehelka | RSS(低维护) | 7375ms | http://tehelka.com/rss |
| `the_age` | The Age | 自定义解析(高维护) | 8438ms | http://www.theage.com.au/text/ |
| `the_baffler` | The Baffler | 自定义解析(高维护) | 5889ms | https://thebaffler.com/issues |
| `the_budget_fashionista` | The Budget Fashionista | RSS(低维护) | 546ms | http://feeds.feedburner.com/TheBudgetFashionista |
| `the_conversation` | The Conversation | RSS(低维护) | 2686ms | https://theconversation.com/au/articles.atom |
| `the_diplomat` | The Diplomat | RSS(低维护) | 7733ms | https://thediplomat.com/category/features/feed |
| `the_federalist` | The Federalist | RSS(低维护) | 6156ms | https://thefederalist.com/feed/ |
| `the_gamer` | The Gamer | RSS(低维护) | 7890ms | https://www.thegamer.com/feed/category/game-guides/ |
| `the_oz` | The Australian | RSS(低维护) | 6311ms | https://www.news.com.au/content-feeds/latest-news-national/ |
| `the_philippine_daily_inquirer` | The Philippine Daily Inquirer | RSS(低维护) | 12765ms | http://newsinfo.inquirer.net/category/inquirer-headlines/feed |
| `the_richest` | The Richest | RSS(低维护) | 6500ms | https://www.therichest.com/feed/category/rich-powerful/ |
| `the_saturday_paper` | The Saturday Paper | 自定义解析(高维护) | 7905ms | https://www.thesaturdaypaper.com.au/news |
| `the_sportster` | The Sportster | RSS(低维护) | 6359ms | https://www.thesportster.com/feed/category/wwe/ |
| `the_sun` | The Sun UK | RSS(低维护) | 5468ms | http://www.thesun.co.uk/sol/homepage/news/rss |
| `the_things` | The Things | RSS(低维护) | 6796ms | https://www.thethings.com/feed/category/celebrity/ |
| `the_travel` | The Travel | RSS(低维护) | 2452ms | https://www.thetravel.com/feed/category/travel-guides/ |
| `the_week` | The Week | 自定义解析(高维护) | 2219ms | https://www.theweek.in/theweek/ |
| `the_week_magazine_free` | The Week | 自定义解析(高维护) | 4280ms | https://theweek.com/archive |
| `the_week_uk` | The Week | 自定义解析(高维护) | 7109ms | https://theweek.com/archive |
| `the_wire` | The Wire | 自定义解析(高维护) | 6781ms | https://thewirehindi.com/home_data_2.json |
| `thedailywtf` | The Daily WTF | RSS(低维护) | 6594ms | http://syndication.thedailywtf.com/TheDailyWtf |
| `theeconomictimes_india` | The Economic Times India | RSS(低维护) | 6063ms | http://economictimes.indiatimes.com/rssfeedstopstories.cms |
| `theeconomictimes_india_print_edition` | The Economic Times | Print Edition | 自定义解析(高维护) | 8703ms | https://economictimes.indiatimes.com/print_edition.cms |
| `theindiaforum` | The India Forum | 自定义解析(高维护) | 6015ms | https://www.theindiaforum.in/ |
| `theonlinephotographer` | The Online Photographer | RSS(低维护) | 531ms | http://feeds.feedburner.com/typepad/ZSjz |
| `thestar` | The Toronto Star | 自定义解析(高维护) | 5952ms | https://www.thestar.com/ |
| `tls_mag` | Times Literary Supplement | 自定义解析(高维护) | 6047ms | https://www.the-tls.com/issues/current-issue/ |
| `toiprint` | TOI Print Edition | 自定义解析(高维护) | 6078ms | https://epaper.indiatimes.com/english-news-paper-today-toi-print-edition/ |
| `top_speed` | Top Speed | RSS(低维护) | 6014ms | https://www.topspeed.com/feed/category/car-news/ |
| `truthout` | Truthout | RSS(低维护) | 6797ms | http://truthout.org/feed?format=feed |
| `uncrate` | Uncrate | RSS(低维护) | 531ms | http://feeds.feedburner.com/uncrate |
| `united_nations` | United Nations | RSS(低维护) | 5436ms | https://news.un.org/feed/subscribe/en/news/all/rss.xml |
| `universe_today` | Universe Today | RSS(低维护) | 6640ms | http://feeds.feedburner.com/universetoday/pYdq |
| `unz` | The Unz Review | RSS(低维护) | 33843ms | https://www.unz.com/feed |
| `variety` | Variety | RSS(低维护) | 968ms | http://feeds.feedburner.com/variety/headlines |
| `vox` | VOX | RSS(低维护) | 1141ms | https://www.vox.com/rss/index.xml |
| `walrusmag` | The Walrus Mag | RSS(低维护) | 5468ms | https://thewalrus.ca/feed/ |
| `winnipeg_free_press` | Winnipeg Free Press | RSS(低维护) | 28391ms | http://www.winnipegfreepress.com/rss?path=/breakingnews |
| `xda` | XDA | RSS(低维护) | 5922ms | https://www.xda-developers.com/feed/news/ |
| `yahoo_news` | Yahoo News | RSS(低维护) | 6343ms | http://rss.news.yahoo.com/rss/topstories |
| `zerohedge` | Zero Hedge | RSS(低维护) | 547ms | http://feeds.feedburner.com/zerohedge/feed |

## 三、无代理直连可用 + RSS 低维护(共 107 个,⭐=学习者推荐)

| 源 ID | 名称 | 延迟 | 主链接 |
|---|---|---|---|
| `adventuregamers` | Adventure Gamers | 9109ms | http://www.adventuregamers.com/rss/ |
| `anandtech` | Anandtech | 1765ms | http://www.anandtech.com/rss/ |
| `apod` | Astronomy Picture of the Day ⭐ | 8578ms | http://apod.nasa.gov/apod.rss |
| `ba_herald` | Buenos Aires Herald | 10640ms | http://www.buenosairesherald.com/argentina |
| `before_we_go` | Before We Go | 3859ms | https://beforewegoblog.com/feed/ |
| `birmingham_evening_mail` | Birmingham Evening Mail | 3655ms | http://www.birminghammail.co.uk/news/local-news/rss.xml |
| `cnetnews` | CNET News ⭐ | 4750ms | http://www.cnet.com/rss/news/ |
| `common_dreams` | Common Dreams | 1265ms | https://www.commondreams.org/feed/headlines_rss |
| `daily_mirror` | The Daily Mirror ⭐ | 3561ms | http://www.mirror.co.uk/news/uk-news/rss.xml |
| `debunkingdenialism` | Debunking Denialism | 2421ms | https://debunkingdenialism.com/feed/ |
| `den_of_geek` | Den of Geek | 4436ms | http://www.denofgeek.com/movies/rss/ |
| `denver_post` | Denver Post | 2905ms | http://feeds.denverpost.com/dp-news-topstories |
| `endgadget` | Engadget ⭐ | 5952ms | https://www.engadget.com/rss.xml |
| `everett_herald` | Everett Herald | 2967ms | http://www.heraldnet.com/feed/ |
| `foxnews` | FOX News ⭐ | 2921ms | http://feeds.foxnews.com/foxnews/latest |
| `freenature` | Nature News ⭐ | 1546ms | http://feeds.nature.com/nature/rss/current |
| `fudzilla` | Fudzilla | 5000ms | http://www.fudzilla.com/?format=feed |
| `gagadget_en` | Gagadget | 1985ms | https://gagadget.com/en/rss/ |
| `github` | Github Blog | 1703ms | https://github.blog/category/engineering/feed/ |
| `good_ereader` | Good e-Reader | 4313ms | https://goodereader.com/blog/feed |
| `good_house_keeping` | Good House Keeping | 1046ms | http://www.goodhousekeeping.com/rss/recipes/ |
| `haaretz_en` | Haaretz | 686ms | https://www.haaretz.com/srv/haaretz-latest-headlines |
| `hackaday` | Hack a Day | 2952ms | https://hackaday.com/blog/feed/ |
| `hackernews` | HN With Actual Comments | 1561ms | https://hnrss.org/frontpage |
| `high_country_news` | High Country News | 2921ms | https://www.hcn.org/rss/most-recent/rss.xml |
| `himal_southasian` | Himal Southasian | 2625ms | https://www.himalmag.com/feed |
| `hindu_post` | Hindu Post ⭐ | 3297ms | https://hindupost.in/feed/ |
| `hinduism_today` | Hinduism Today | 2500ms | https://www.hinduismtoday.com/feed/ |
| `ieeespectrum` | IEEE Spectrum Online | 17609ms | https://spectrum.ieee.org/rss/fulltext |
| `india_facts` | IndiaFacts | 7953ms | https://www.indiafacts.org.in/feed/ |
| `instapaper` | Instapaper | 9921ms | https://www.instapaper.com/u |
| `intelligencer` | Intelligencer | 609ms | http://www.intelligencer.ca/rss/ |
| `interfax` | Interfax-Ukraine | 3203ms | https://en.interfax.com.ua/news/last.rss |
| `jpost` | Jerusalem Post | 1358ms | https://www.jpost.com/Rss/RssFeedsHeadlines.aspx |
| `korea_herald` | KoreaHerald ⭐ | 766ms | http://www.koreaherald.com/rss/020100000000.xml |
| `kyivpost_en` | Kyiv Post | 2155ms | https://www.kyivpost.com/feed |
| `las_vegas_review` | Las Vegas Review Journal | 2546ms | http://www.reviewjournal.com/rss.xml |
| `lex_fridman_podcast` | Lex Fridman Podcast | 3375ms | https://lexfridman.com/feed/podcast/ |
| `lifehacker` | LifeHacker | 375ms | https://lifehacker.com/feed/rss |
| `lightspeed_magazine` | Lightspeed Magazine | 5046ms | http://www.lightspeedmagazine.com/rss-2/ |
| `linux_magazine` | Linux Magazine | 3984ms | http://www.linux-magazine.com/rss/feed/lmi_full |
| `list_apart` | A List Apart | 1561ms | https://alistapart.com/main/feed |
| `livescience` | Live Science | 1061ms | https://www.livescience.com/feeds/all |
| `ludwig_mises` | Ludwig von Mises Institute | 2234ms | http://feed.mises.org/MisesFullTextArticles |
| `lwn_free` | LWN Linux Weekly News (Free) | 3030ms | https://lwn.net/headlines/Features |
| `macrobusiness` | Macrobusiness | 2500ms | https://www.macrobusiness.com.au/feed |
| `mail_and_guardian` | Mail & Guardian ZA News ⭐ | 3594ms | http://www.mg.co.za/rss/national |
| `martinfowler` | Martin Fowler Blog | 2436ms | https://martinfowler.com/feed.atom |
| `meduza` | Meduza | 1141ms | https://meduza.io/rss2/en/news |
| `moscow_times` | The Moscow Times (light version) | 2827ms | https://themoscowtimes.com/feeds/main.xml |
| `moscowtimes_en` | The Moscow Times ⭐ | 921ms | https://www.themoscowtimes.com/rss/news |
| `nakedcapitalism` | Naked Capitalism | 1890ms | https://www.nakedcapitalism.com/feed |
| `nasa` | NASA ⭐ | 1436ms | https://www.nasa.gov/rss/dyn/breaking_news.rss |
| `nautilus` | Nautilus Magazine | 2094ms | https://nautil.us/topics/anthropology/feed |
| `new_scientist` | New Scientist - Online News w. subscription | 1186ms | https://www.newscientist.com/section/news/feed/ |
| `news_busters` | News Busters | 4390ms | http://www.newsbusters.org/rss.xml |
| `newslaundry` | Newslaundry | 1390ms | https://www.newslaundry.com/stories.rss?time-period=last-7-days |
| `nme` | New Musical Express Magazine | 14859ms | http://www.nme.com/news/feed |
| `novaya_gazeta_europe_en` | Novaya Gazeta Europe | 890ms | https://novayagazeta.eu/feed/rss/en |
| `npr` | National Public Radio ⭐ | 625ms | http://www.npr.org/rss/rss.php?id=1001 |
| `nv_en` | NV (The New Voice of Ukraine) | 2280ms | https://english.nv.ua/rss/all_english.xml |
| `nzherald` | New Zealand Herald | 3219ms | http://rss.nzherald.co.nz/rss/xml/nzhrsscid_000000003.xml |
| `oilprice` | Oil Price | 2032ms | http://www.oilprice.com/rss.xml |
| `oldnewthing` | The Old New Thing | 3532ms | https://blogs.msdn.microsoft.com/oldnewthing/feed |
| `omgubuntu` | Omg! Ubuntu! | 1811ms | https://www.omgubuntu.co.uk/feed |
| `pc_advisor` | Pc Advisor  | 4859ms | http://www.pcadvisor.co.uk/latest/rss |
| `phoronix` | Phoronix | 719ms | https://www.phoronix.com/rss.php |
| `phys_org` | PhysOrg | 2250ms | http://phys.org/rss-feed/nanotech-news/ |
| `planet_kde` | KDE News | 2891ms | http://planetkde.org/rss20.xml |
| `planet_python` | Planet Python | 1984ms | http://planetpython.org/rss20.xml |
| `pragyata` | Pragyata | 12625ms | https://pragyata.com/feed/ |
| `prekshaa` | prekshaa | 2686ms | https://www.prekshaa.in/feed |
| `propublica` | Pro Publica | 4141ms | http://feeds.propublica.org/propublica/main |
| `publicdomainreview_org` | The Public Domain Review | 1860ms | http://publicdomainreview.org/feed/ |
| `quanta_magazine` | Quanta Magazine | 1328ms | https://api.quantamagazine.org/feed/ |
| `queueacmorg` | ACM Queue Magazine | 1046ms | https://queue.acm.org/rss/feeds/queuecontent.xml |
| `rds` | RDS | 3655ms | http://www.rds.ca/hockey/fildepresse_rds.xml |
| `real_world_economics_review` | Real-world economis review blog | 3421ms | http://rwer.wordpress.com/feed/ |
| `rte` | RTE News ⭐ | 2157ms | http://www.rte.ie/rss/news.xml |
| `rtnews` | Russia Today | 14375ms | https://www.rt.com/rss/russia/ |
| `russiafeed` | RussiaFeed News | 4561ms | http://russiafeed.com/category/news/feed/ |
| `salon` | Salon.com | 5671ms | https://www.salon.com/category/news-and-politics/feed |
| `san_fran_chronicle` | San Francisco Chronicle | 1609ms | https://www.sfgate.com/bayarea/feed/Bay-Area-News-429.php |
| `science_x` | Science X | 1515ms | https://techxplore.com/rss-feed/ |
| `sfbg` | San Francisco Bay Guardian | 2030ms | http://www.sfbg.com/feed/ |
| `sign_of_the_times` | Sign of the Times | 2828ms | http://www.sott.net/xml_engine/signs_rss |
| `slashdot` | Slashdot.org | 5157ms | http://rss.slashdot.org/Slashdot/slashdot |
| `smashing` | Smashing Magazine | 4015ms | http://rss1.smashingmagazine.com/feed/ |
| `sonar21` | Sonar21 | 6063ms | https://sonar21.com/feed |
| `stackoverflow` | Stack Overflow - Blog | 1936ms | http://blog.stackoverflow.com/feed/ |
| `standardmedia_ke` | The Standard ⭐ | 1530ms | http://www.standardmedia.co.ke/rss/headlines.php |
| `strange_horizons` | Strange Horizons | 5030ms | http://strangehorizons.com/wordpress/feed/ |
| `stratechery` | Stratechery | 2421ms | https://stratechery.com/feed/ |
| `techcrunch` | TechCrunch | 765ms | https://techcrunch.com/feed/ |
| `teleread` | Teleread Blog | 4109ms | http://www.teleread.org/feed/ |
| `the_ebook_reader` | The eBook Reader | 5702ms | https://blog.the-ebook-reader.com/feed/ |
| `the_journal` | TheJournal.ie | 1733ms | http://www.thejournal.ie/feed/ |
| `the_nation` | The Nation | 7000ms | http://www.thenation.com/rss/articles |
| `the_register` | The Register | 1280ms | http://www.theregister.co.uk/headlines.atom |
| `the_verge` | The Verge | 11047ms | http://www.theverge.com/rss/index.xml |
| `theecocolapse` | The Economic Collapse | 4625ms | http://theeconomiccollapseblog.com/feed |
| `tillsonburg` | Tillsonburg/Norfolk County | 563ms | http://www.simcoereformer.ca/rss/ |
| `tmz` | The TMZ | 15375ms | http://www.tmz.com/rss.xml |
| `ukrinform_en` | UkrInform (English) | 1561ms | https://www.ukrinform.net/rss/block-lastnews |
| `unian_net_en` | UNIAN | 4436ms | https://rss.unian.net/site/news_eng.rss |
| `wired_daily` | Wired Daily Edition | 1764ms | https://www.wired.com/feed/rss |
| `znetwork` | ZNetwork | 1922ms | https://znetwork.org/feed/ |

> ⭐ = 学习者友好推荐(内容定位适合英语学习,人工标注)

## 四、全部英文候选源明细(按无代理可用性排序)

| 源 ID | 名称 | 语言 | 类型 | 无代理 | 代理 | 延迟 | 备注 | 测试链接 |
|---|---|---|---|---|---|---|---|---|
| `adventuregamers` | Adventure Gamers | en | RSS(低维护) | ✅ 可用 | ✅ 可用 | 9109ms | - | http://www.adventuregamers.com/rss/ |
| `anandtech` | Anandtech | en | RSS(低维护) | ✅ 可用 | ✅ 可用 | 1765ms | - | http://www.anandtech.com/rss/ |
| `apod` | Astronomy Picture of the Day | en | RSS(低维护) | ✅ 可用 | ✅ 可用 | 8578ms | - | http://apod.nasa.gov/apod.rss |
| `ba_herald` | Buenos Aires Herald | en_AR | RSS(低维护) | ✅ 可用 | ✅ 可用 | 10640ms | - | http://www.buenosairesherald.com/argentina |
| `before_we_go` | Before We Go | en | RSS(低维护) | ✅ 可用 | ✅ 可用 | 3859ms | - | https://beforewegoblog.com/feed/ |
| `birmingham_evening_mail` | Birmingham Evening Mail | en_GB | RSS(低维护) | ✅ 可用 | ✅ 可用 | 3655ms | - | http://www.birminghammail.co.uk/news/local-news/rss.xml |
| `cnetnews` | CNET News | en | RSS(低维护) | ✅ 可用 | ✅ 可用 | 4750ms | - | http://www.cnet.com/rss/news/ |
| `common_dreams` | Common Dreams | en | RSS(低维护) | ✅ 可用 | ✅ 可用 | 1265ms | - | https://www.commondreams.org/feed/headlines_rss |
| `daily_mirror` | The Daily Mirror | en_GB | RSS(低维护) | ✅ 可用 | ✅ 可用 | 3561ms | - | http://www.mirror.co.uk/news/uk-news/rss.xml |
| `debunkingdenialism` | Debunking Denialism | en | RSS(低维护) | ✅ 可用 | ✅ 可用 | 2421ms | - | https://debunkingdenialism.com/feed/ |
| `den_of_geek` | Den of Geek | en | RSS(低维护) | ✅ 可用 | ✅ 可用 | 4436ms | - | http://www.denofgeek.com/movies/rss/ |
| `denver_post` | Denver Post | en_US | RSS(低维护) | ✅ 可用 | ✅ 可用 | 2905ms | - | http://feeds.denverpost.com/dp-news-topstories |
| `endgadget` | Engadget | en | RSS(低维护) | ✅ 可用 | ✅ 可用 | 5952ms | - | https://www.engadget.com/rss.xml |
| `everett_herald` | Everett Herald | en | RSS(低维护) | ✅ 可用 | ✅ 可用 | 2967ms | - | http://www.heraldnet.com/feed/ |
| `foxnews` | FOX News | en_US | RSS(低维护) | ✅ 可用 | ✅ 可用 | 2921ms | - | http://feeds.foxnews.com/foxnews/latest |
| `freenature` | Nature News | en | RSS(低维护) | ✅ 可用 | ✅ 可用 | 1546ms | - | http://feeds.nature.com/nature/rss/current |
| `fudzilla` | Fudzilla | en | RSS(低维护) | ✅ 可用 | ✅ 可用 | 5000ms | - | http://www.fudzilla.com/?format=feed |
| `gagadget_en` | Gagadget | en_UK | RSS(低维护) | ✅ 可用 | ✅ 可用 | 1985ms | - | https://gagadget.com/en/rss/ |
| `github` | Github Blog | en | RSS(低维护) | ✅ 可用 | ✅ 可用 | 1703ms | - | https://github.blog/category/engineering/feed/ |
| `good_ereader` | Good e-Reader | en | RSS(低维护) | ✅ 可用 | ✅ 可用 | 4313ms | - | https://goodereader.com/blog/feed |
| `good_house_keeping` | Good House Keeping | en | RSS(低维护) | ✅ 可用 | ✅ 可用 | 1046ms | - | http://www.goodhousekeeping.com/rss/recipes/ |
| `haaretz_en` | Haaretz | en_IL | RSS(低维护) | ✅ 可用 | ✅ 可用 | 686ms | 需订阅 | https://www.haaretz.com/srv/haaretz-latest-headlines |
| `hackaday` | Hack a Day | en-US | RSS(低维护) | ✅ 可用 | ✅ 可用 | 2952ms | - | https://hackaday.com/blog/feed/ |
| `hackernews` | HN With Actual Comments | en | RSS(低维护) | ✅ 可用 | ✅ 可用 | 1561ms | - | https://hnrss.org/frontpage |
| `high_country_news` | High Country News | en | RSS(低维护) | ✅ 可用 | ✅ 可用 | 2921ms | - | https://www.hcn.org/rss/most-recent/rss.xml |
| `himal_southasian` | Himal Southasian | en_IN | RSS(低维护) | ✅ 可用 | ✅ 可用 | 2625ms | - | https://www.himalmag.com/feed |
| `hindu_post` | Hindu Post | en_IN | RSS(低维护) | ✅ 可用 | ✅ 可用 | 3297ms | - | https://hindupost.in/feed/ |
| `hinduism_today` | Hinduism Today | en_IN | RSS(低维护) | ✅ 可用 | ✅ 可用 | 2500ms | - | https://www.hinduismtoday.com/feed/ |
| `ieeespectrum` | IEEE Spectrum Online | en | RSS(低维护) | ✅ 可用 | ✅ 可用 | 17609ms | - | https://spectrum.ieee.org/rss/fulltext |
| `india_facts` | IndiaFacts | en_IN | RSS(低维护) | ✅ 可用 | ✅ 可用 | 7953ms | - | https://www.indiafacts.org.in/feed/ |
| `instapaper` | Instapaper | en | RSS(低维护) | ✅ 可用 | ✅ 可用 | 9921ms | 需订阅 | https://www.instapaper.com/u |
| `intelligencer` | Intelligencer | en | RSS(低维护) | ✅ 可用 | ✅ 可用 | 609ms | - | http://www.intelligencer.ca/rss/ |
| `interfax` | Interfax-Ukraine | en_UK | RSS(低维护) | ✅ 可用 | ✅ 可用 | 3203ms | - | https://en.interfax.com.ua/news/last.rss |
| `jpost` | Jerusalem Post | en | RSS(低维护) | ✅ 可用 | ✅ 可用 | 1358ms | - | https://www.jpost.com/Rss/RssFeedsHeadlines.aspx |
| `korea_herald` | KoreaHerald | en | RSS(低维护) | ✅ 可用 | ✅ 可用 | 766ms | - | http://www.koreaherald.com/rss/020100000000.xml |
| `kyivpost_en` | Kyiv Post | en_UK | RSS(低维护) | ✅ 可用 | ✅ 可用 | 2155ms | - | https://www.kyivpost.com/feed |
| `las_vegas_review` | Las Vegas Review Journal | en_US | RSS(低维护) | ✅ 可用 | ✅ 可用 | 2546ms | - | http://www.reviewjournal.com/rss.xml |
| `lex_fridman_podcast` | Lex Fridman Podcast | en | RSS(低维护) | ✅ 可用 | ✅ 可用 | 3375ms | - | https://lexfridman.com/feed/podcast/ |
| `lifehacker` | LifeHacker | en | RSS(低维护) | ✅ 可用 | ✅ 可用 | 375ms | - | https://lifehacker.com/feed/rss |
| `lightspeed_magazine` | Lightspeed Magazine | en | RSS(低维护) | ✅ 可用 | ✅ 可用 | 5046ms | - | http://www.lightspeedmagazine.com/rss-2/ |
| `linux_magazine` | Linux Magazine | en | RSS(低维护) | ✅ 可用 | ✅ 可用 | 3984ms | - | http://www.linux-magazine.com/rss/feed/lmi_full |
| `list_apart` | A List Apart | en | RSS(低维护) | ✅ 可用 | ✅ 可用 | 1561ms | - | https://alistapart.com/main/feed |
| `livescience` | Live Science | en | RSS(低维护) | ✅ 可用 | ✅ 可用 | 1061ms | - | https://www.livescience.com/feeds/all |
| `ludwig_mises` | Ludwig von Mises Institute | en | RSS(低维护) | ✅ 可用 | ✅ 可用 | 2234ms | - | http://feed.mises.org/MisesFullTextArticles |
| `lwn_free` | LWN Linux Weekly News (Free) | en | RSS(低维护) | ✅ 可用 | ✅ 可用 | 3030ms | - | https://lwn.net/headlines/Features |
| `macrobusiness` | Macrobusiness | en_AU | RSS(低维护) | ✅ 可用 | ✅ 可用 | 2500ms | - | https://www.macrobusiness.com.au/feed |
| `mail_and_guardian` | Mail & Guardian ZA News | en_ZA | RSS(低维护) | ✅ 可用 | ✅ 可用 | 3594ms | - | http://www.mg.co.za/rss/national |
| `martinfowler` | Martin Fowler Blog | en | RSS(低维护) | ✅ 可用 | ✅ 可用 | 2436ms | - | https://martinfowler.com/feed.atom |
| `meduza` | Meduza | en_RU | RSS(低维护) | ✅ 可用 | ✅ 可用 | 1141ms | - | https://meduza.io/rss2/en/news |
| `moscow_times` | The Moscow Times (light version) | en_RU | RSS(低维护) | ✅ 可用 | ✅ 可用 | 2827ms | - | https://themoscowtimes.com/feeds/main.xml |
| `moscowtimes_en` | The Moscow Times | en_RU | RSS(低维护) | ✅ 可用 | ✅ 可用 | 921ms | - | https://www.themoscowtimes.com/rss/news |
| `nakedcapitalism` | Naked Capitalism | ? | RSS(低维护) | ✅ 可用 | ✅ 可用 | 1890ms | no_language | https://www.nakedcapitalism.com/feed |
| `nasa` | NASA | en | RSS(低维护) | ✅ 可用 | ✅ 可用 | 1436ms | - | https://www.nasa.gov/rss/dyn/breaking_news.rss |
| `nautilus` | Nautilus Magazine | en_US | RSS(低维护) | ✅ 可用 | ✅ 可用 | 2094ms | - | https://nautil.us/topics/anthropology/feed |
| `new_scientist` | New Scientist - Online News w. subscription | en | RSS(低维护) | ✅ 可用 | ✅ 可用 | 1186ms | - | https://www.newscientist.com/section/news/feed/ |
| `news_busters` | News Busters | en | RSS(低维护) | ✅ 可用 | ✅ 可用 | 4390ms | - | http://www.newsbusters.org/rss.xml |
| `newslaundry` | Newslaundry | en_IN | RSS(低维护) | ✅ 可用 | ✅ 可用 | 1390ms | - | https://www.newslaundry.com/stories.rss?time-period=last-7-days |
| `nme` | New Musical Express Magazine | en | RSS(低维护) | ✅ 可用 | ✅ 可用 | 14859ms | - | http://www.nme.com/news/feed |
| `novaya_gazeta_europe_en` | Novaya Gazeta Europe | en_RU | RSS(低维护) | ✅ 可用 | ✅ 可用 | 890ms | - | https://novayagazeta.eu/feed/rss/en |
| `npr` | National Public Radio | en | RSS(低维护) | ✅ 可用 | ✅ 可用 | 625ms | - | http://www.npr.org/rss/rss.php?id=1001 |
| `nv_en` | NV (The New Voice of Ukraine) | en_UK | RSS(低维护) | ✅ 可用 | ✅ 可用 | 2280ms | - | https://english.nv.ua/rss/all_english.xml |
| `nzherald` | New Zealand Herald | en_NZ | RSS(低维护) | ✅ 可用 | ✅ 可用 | 3219ms | - | http://rss.nzherald.co.nz/rss/xml/nzhrsscid_000000003.xml |
| `oilprice` | Oil Price | en | RSS(低维护) | ✅ 可用 | ✅ 可用 | 2032ms | - | http://www.oilprice.com/rss.xml |
| `oldnewthing` | The Old New Thing | en | RSS(低维护) | ✅ 可用 | ✅ 可用 | 3532ms | - | https://blogs.msdn.microsoft.com/oldnewthing/feed |
| `omgubuntu` | Omg! Ubuntu! | en | RSS(低维护) | ✅ 可用 | ✅ 可用 | 1811ms | - | https://www.omgubuntu.co.uk/feed |
| `pc_advisor` | Pc Advisor  | en | RSS(低维护) | ✅ 可用 | ✅ 可用 | 4859ms | - | http://www.pcadvisor.co.uk/latest/rss |
| `phoronix` | Phoronix | en | RSS(低维护) | ✅ 可用 | ✅ 可用 | 719ms | 需浏览器渲染 | https://www.phoronix.com/rss.php |
| `phys_org` | PhysOrg | en | RSS(低维护) | ✅ 可用 | ✅ 可用 | 2250ms | - | http://phys.org/rss-feed/nanotech-news/ |
| `planet_kde` | KDE News | en | RSS(低维护) | ✅ 可用 | ✅ 可用 | 2891ms | - | http://planetkde.org/rss20.xml |
| `planet_python` | Planet Python | en | RSS(低维护) | ✅ 可用 | ✅ 可用 | 1984ms | - | http://planetpython.org/rss20.xml |
| `pragyata` | Pragyata | en_IN | RSS(低维护) | ✅ 可用 | ✅ 可用 | 12625ms | - | https://pragyata.com/feed/ |
| `prekshaa` | prekshaa | en_IN | RSS(低维护) | ✅ 可用 | ✅ 可用 | 2686ms | - | https://www.prekshaa.in/feed |
| `propublica` | Pro Publica | en | RSS(低维护) | ✅ 可用 | ✅ 可用 | 4141ms | - | http://feeds.propublica.org/propublica/main |
| `publicdomainreview_org` | The Public Domain Review | en | RSS(低维护) | ✅ 可用 | ✅ 可用 | 1860ms | - | http://publicdomainreview.org/feed/ |
| `quanta_magazine` | Quanta Magazine | en | RSS(低维护) | ✅ 可用 | ✅ 可用 | 1328ms | - | https://api.quantamagazine.org/feed/ |
| `queueacmorg` | ACM Queue Magazine | en | RSS(低维护) | ✅ 可用 | ✅ 可用 | 1046ms | - | https://queue.acm.org/rss/feeds/queuecontent.xml |
| `rds` | RDS | en_CA | RSS(低维护) | ✅ 可用 | ✅ 可用 | 3655ms | - | http://www.rds.ca/hockey/fildepresse_rds.xml |
| `real_world_economics_review` | Real-world economis review blog | en | RSS(低维护) | ✅ 可用 | ✅ 可用 | 3421ms | - | http://rwer.wordpress.com/feed/ |
| `rte` | RTE News | en_IE | RSS(低维护) | ✅ 可用 | ✅ 可用 | 2157ms | - | http://www.rte.ie/rss/news.xml |
| `rtnews` | Russia Today | en | RSS(低维护) | ✅ 可用 | ✅ 可用 | 14375ms | - | https://www.rt.com/rss/russia/ |
| `russiafeed` | RussiaFeed News | en_RU | RSS(低维护) | ✅ 可用 | ✅ 可用 | 4561ms | - | http://russiafeed.com/category/news/feed/ |
| `salon` | Salon.com | en | RSS(低维护) | ✅ 可用 | ✅ 可用 | 5671ms | - | https://www.salon.com/category/news-and-politics/feed |
| `san_fran_chronicle` | San Francisco Chronicle | en_US | RSS(低维护) | ✅ 可用 | ✅ 可用 | 1609ms | - | https://www.sfgate.com/bayarea/feed/Bay-Area-News-429.php |
| `science_x` | Science X | en | RSS(低维护) | ✅ 可用 | ✅ 可用 | 1515ms | - | https://techxplore.com/rss-feed/ |
| `sfbg` | San Francisco Bay Guardian | en_US | RSS(低维护) | ✅ 可用 | ✅ 可用 | 2030ms | - | http://www.sfbg.com/feed/ |
| `sign_of_the_times` | Sign of the Times | en | RSS(低维护) | ✅ 可用 | ✅ 可用 | 2828ms | - | http://www.sott.net/xml_engine/signs_rss |
| `slashdot` | Slashdot.org | en | RSS(低维护) | ✅ 可用 | ✅ 可用 | 5157ms | - | http://rss.slashdot.org/Slashdot/slashdot |
| `smashing` | Smashing Magazine | en | RSS(低维护) | ✅ 可用 | ✅ 可用 | 4015ms | - | http://rss1.smashingmagazine.com/feed/ |
| `sonar21` | Sonar21 | en_US | RSS(低维护) | ✅ 可用 | ✅ 可用 | 6063ms | 需浏览器渲染 | https://sonar21.com/feed |
| `standardmedia_ke` | The Standard | en | RSS(低维护) | ✅ 可用 | ✅ 可用 | 1530ms | - | http://www.standardmedia.co.ke/rss/headlines.php |
| `strange_horizons` | Strange Horizons | en | RSS(低维护) | ✅ 可用 | ✅ 可用 | 5030ms | - | http://strangehorizons.com/wordpress/feed/ |
| `stratechery` | Stratechery | en | RSS(低维护) | ✅ 可用 | ✅ 可用 | 2421ms | - | https://stratechery.com/feed/ |
| `techcrunch` | TechCrunch | en | RSS(低维护) | ✅ 可用 | ✅ 可用 | 765ms | - | https://techcrunch.com/feed/ |
| `teleread` | Teleread Blog | en | RSS(低维护) | ✅ 可用 | ✅ 可用 | 4109ms | - | http://www.teleread.org/feed/ |
| `the_ebook_reader` | The eBook Reader | en | RSS(低维护) | ✅ 可用 | ✅ 可用 | 5702ms | - | https://blog.the-ebook-reader.com/feed/ |
| `the_journal` | TheJournal.ie | en_IE | RSS(低维护) | ✅ 可用 | ✅ 可用 | 1733ms | - | http://www.thejournal.ie/feed/ |
| `the_nation` | The Nation | en | RSS(低维护) | ✅ 可用 | ✅ 可用 | 7000ms | - | http://www.thenation.com/rss/articles |
| `the_register` | The Register | en | RSS(低维护) | ✅ 可用 | ✅ 可用 | 1280ms | - | http://www.theregister.co.uk/headlines.atom |
| `the_verge` | The Verge | en | RSS(低维护) | ✅ 可用 | ✅ 可用 | 11047ms | - | http://www.theverge.com/rss/index.xml |
| `theecocolapse` | The Economic Collapse | en | RSS(低维护) | ✅ 可用 | ✅ 可用 | 4625ms | - | http://theeconomiccollapseblog.com/feed |
| `tillsonburg` | Tillsonburg/Norfolk County | en_CA | RSS(低维护) | ✅ 可用 | ✅ 可用 | 563ms | - | http://www.simcoereformer.ca/rss/ |
| `tmz` | The TMZ | en_US | RSS(低维护) | ✅ 可用 | ✅ 可用 | 15375ms | - | http://www.tmz.com/rss.xml |
| `ukrinform_en` | UkrInform (English) | en_UK | RSS(低维护) | ✅ 可用 | ✅ 可用 | 1561ms | - | https://www.ukrinform.net/rss/block-lastnews |
| `unian_net_en` | UNIAN | en_UK | RSS(低维护) | ✅ 可用 | ✅ 可用 | 4436ms | - | https://rss.unian.net/site/news_eng.rss |
| `wired_daily` | Wired Daily Edition | en | RSS(低维护) | ✅ 可用 | ✅ 可用 | 1764ms | - | https://www.wired.com/feed/rss |
| `znetwork` | ZNetwork | en | RSS(低维护) | ✅ 可用 | ✅ 可用 | 1922ms | - | https://znetwork.org/feed/ |
| `ancient_egypt` | The Past: Ancient Egypt Magazine | en | 自定义解析(高维护) | ✅ 可用 | ✅ 可用 | 5453ms | - | https://the-past.com/category/magazines/ae/ |
| `ap` | Associated Press | en | 自定义解析(高维护) | ✅ 可用 | ✅ 可用 | 1453ms | - | https://apnews.com |
| `arcamax` | Arcamax | en | 自定义解析(高维护) | ✅ 可用 | ✅ 可用 | 3250ms | - | https://www.arcamax.com/thefunnies/bc |
| `bar_and_bench` | Bar and Bench | en_IN | 自定义解析(高维护) | ✅ 可用 | ✅ 可用 | 2155ms | - | https://www.barandbench.com/ |
| `bookforummagazine` | Bookforum | en | 自定义解析(高维护) | ✅ 可用 | ✅ 可用 | 2297ms | - | https://www.bookforum.com/print |
| `boston_globe_print_edition` | Boston Globe | Print Edition | en_US | 自定义解析(高维护) | ✅ 可用 | ✅ 可用 | 5390ms | - | https://www.bostonglobe.com/todays-paper/ |
| `business_today` | Business Today Magazine | en_IN | 自定义解析(高维护) | ✅ 可用 | ✅ 可用 | 1969ms | - | https://www.businesstoday.in/magazine/issue/ |
| `caravan_magazine` | Caravan Magazine | en_IN | 自定义解析(高维护) | ✅ 可用 | ✅ 可用 | 921ms | - | https://api.caravanmagazine.in/api/trpc/magazines.getLatestIssue |
| `democracy_journal` | Democracy Journal | en | 自定义解析(高维护) | ✅ 可用 | ✅ 可用 | 3391ms | - | http://www.democracyjournal.org |
| `discover_magazine_monthly` | Discover Magazine Monthly | en | 自定义解析(高维护) | ✅ 可用 | ✅ 可用 | 2155ms | 需订阅 | http://www.discovermagazine.com |
| `distrowatch_weekly` | DistroWatch Weekly | en | 自定义解析(高维护) | ✅ 可用 | ✅ 可用 | 5265ms | - | https://distrowatch.com/weekly.php?issue=%Y%m%d |
| `economia` | Economia Magazine | en_GB | 自定义解析(高维护) | ✅ 可用 | ✅ 可用 | 9219ms | - | http://economia.icaew.com/ |
| `esquire` | Esquire | en | 自定义解析(高维护) | ✅ 可用 | ✅ 可用 | 1594ms | - | https://www.esquire.com |
| `fortune_magazine` | Fortune Magazine | en | 自定义解析(高维护) | ✅ 可用 | ✅ 可用 | 5671ms | - | https://fortune.com/section/magazine/ |
| `galaxys_edge` | The Galaxy's Edge | en | 自定义解析(高维护) | ✅ 可用 | ✅ 可用 | 1828ms | - | http://www.galaxysedge.com/ |
| `harpers` | Harper’s Magazine | en_US | 自定义解析(高维护) | ✅ 可用 | ✅ 可用 | 2030ms | - | https://harpers.org/issues/ |
| `hbr` | Harvard Business Review | en | 自定义解析(高维护) | ✅ 可用 | ✅ 可用 | 10672ms | - | https://hbr.org/archive-toc/BR |
| `horizons` | Horizons | en | 自定义解析(高维护) | ✅ 可用 | ✅ 可用 | 3750ms | - | https://www.cirsd.org/en/horizons |
| `inc42` | Inc42 | en_IN | 自定义解析(高维护) | ✅ 可用 | ✅ 可用 | 3733ms | - | https://inc42.com/ |
| `irish_times` | The Irish Times | en_IE | 自定义解析(高维护) | ✅ 可用 | ✅ 可用 | 1438ms | 需订阅 | https://www.irishtimes.com/ |
| `irish_times_free` | The Irish Times (free) | en_IE | 自定义解析(高维护) | ✅ 可用 | ✅ 可用 | 1375ms | - | https://www.irishtimes.com/ |
| `journalofaccountancy` | Journal of Accountancy | en | 自定义解析(高维护) | ✅ 可用 | ✅ 可用 | 3032ms | - | https://www.journalofaccountancy.com/issues.html |
| `latimes` | Los Angeles Times | en_US | 自定义解析(高维护) | ✅ 可用 | ✅ 可用 | 4813ms | - | https://www.latimes.com/ |
| `live_law` | Live Law | en_IN | 自定义解析(高维护) | ✅ 可用 | ✅ 可用 | 1782ms | - | https://www.livelaw.in/ |
| `military_history` | The Past: Military History Matters | en | 自定义解析(高维护) | ✅ 可用 | ✅ 可用 | 7217ms | - | https://the-past.com/category/magazines/mhm/ |
| `minerva_magazine` | The Past: Minerva Magazine | en | 自定义解析(高维护) | ✅ 可用 | ✅ 可用 | 4421ms | - | https://the-past.com/category/magazines/minerva/ |
| `mit_technology_review` | MIT Technology Review Magazine | en | 自定义解析(高维护) | ✅ 可用 | ✅ 可用 | 2328ms | - | http://www.technologyreview.com/magazine/ |
| `moneycontrol` | Money Control | en_IN | 自定义解析(高维护) | ✅ 可用 | ✅ 可用 | 2219ms | - | https://www.moneycontrol.com/ |
| `natgeo` | National Geographic | en | 自定义解析(高维护) | ✅ 可用 | ✅ 可用 | 14155ms | - | https://www.nationalgeographic.com/animals |
| `natgeo_kids` | National Geographic Kids | en | 自定义解析(高维护) | ✅ 可用 | ✅ 可用 | 2407ms | - | https://kids.nationalgeographic.com/ |
| `natgeo_traveller` | National Geographic Traveller | en | 自定义解析(高维护) | ✅ 可用 | ✅ 可用 | 4921ms | - | https://www.nationalgeographic.com/travel/topic/national-geographic-traveller-uk |
| `natgeohis` | National Geographic History | en | 自定义解析(高维护) | ✅ 可用 | ✅ 可用 | 6671ms | - | https://www.nationalgeographic.com/history/history-magazine |
| `new_scientist_mag` | New Scientist Magazine | en | 自定义解析(高维护) | ✅ 可用 | ✅ 可用 | 2266ms | - | https://www.newscientist.com/issues/current/ |
| `outlook_business_magazine` | Outlook Business Magazine | en_IN | 自定义解析(高维护) | ✅ 可用 | ✅ 可用 | 2094ms | - | https://www.outlookbusiness.com/magazine/ |
| `phillosophy_now` | Philosophy Now | en | 自定义解析(高维护) | ✅ 可用 | ✅ 可用 | 1827ms | - | https://philosophynow.org/ |
| `science_news` | Science News | en | 自定义解析(高维护) | ✅ 可用 | ✅ 可用 | 922ms | - | https://www.sciencenews.org/sn-magazine |
| `scientific_american` | Scientific American | en | 自定义解析(高维护) | ✅ 可用 | ✅ 可用 | 1140ms | - | https://www.scientificamerican.com |
| `scroll` | Scroll.in | en_IN | 自定义解析(高维护) | ✅ 可用 | ✅ 可用 | 1358ms | - | https://scroll.in/ |
| `seminar_magazine` | Seminar Magazine | en_IN | 自定义解析(高维护) | ✅ 可用 | ✅ 可用 | 1547ms | - | https://www.india-seminar.com/semframe.html |
| `skeptical_enquirer` | The Skeptical Inquirer | en | 自定义解析(高维护) | ✅ 可用 | ✅ 可用 | 6609ms | 需订阅 | https://skepticalinquirer.org/latest/ |
| `the_friday_times` | The Friday Times | en_PK | 自定义解析(高维护) | ✅ 可用 | ✅ 可用 | 8484ms | - | http://www.thefridaytimes.com/tft/ |
| `times_online` | The Times and Sunday Times | en_GB | 自定义解析(高维护) | ✅ 可用 | ✅ 可用 | 5594ms | 需浏览器渲染 | https://www.thetimes.com/ |
| `world_archeology` | The Past: Current World Archaeology | en | 自定义解析(高维护) | ✅ 可用 | ✅ 可用 | 2140ms | - | https://the-past.com/category/magazines/cwa/ |
| `xkcd` | xkcd | en | 自定义解析(高维护) | ✅ 可用 | ✅ 可用 | 2485ms | - | http://xkcd.com/archive/ |
| `billorielly` | Bill O'Reilly | en | 自定义解析(高维护) | ✅ 可用 | ⚠️ 可达被拒 | 8609ms | - | http://www.billoreilly.com/show?action=tvShowArchive |
| `chronicle_higher_ed` | The Chronicle of Higher Education | en | 自定义解析(高维护) | ✅ 可用 | ⚠️ 可达被拒 | 8030ms | 需订阅 | http://chronicle.com/section/Archives/39/ |
| `stackoverflow` | Stack Overflow - Blog | en | RSS(低维护) | ✅ 可用 | http_502 | 1936ms | - | http://blog.stackoverflow.com/feed/ |
| `fairbanks_daily` | Fairbanks Daily News-miner | en | RSS(低维护) | ⚠️ 可达被拒 | ✅ 可用 | 7328ms* | - | http://www.newsminer.com/search/?f=rss&t=article&c=news/alaska_news&l=50&s=start_time&sd=desc |
| `glasgow_herald` | Glasgow Herald | en_GB | RSS(低维护) | ⚠️ 可达被拒 | ✅ 可用 | 5843ms* | - | https://www.heraldscotland.com/news/rss/ |
| `greatist` | Greatist | en | RSS(低维护) | ⚠️ 可达被拒 | ✅ 可用 | 6093ms* | - | https://greatist.com/feed |
| `greensboro_news_and_record` | Greensboro News & Record | en | RSS(低维护) | ⚠️ 可达被拒 | ✅ 可用 | 6686ms* | - | http://www.greensboro.com/search/?q=&t=article&l=10&d=&d1=&d2=&s=start_time&sd=desc&c[]=news,news/*&f=rss |
| `healthline` | Healthline | en | RSS(低维护) | ⚠️ 可达被拒 | ✅ 可用 | 718ms* | - | https://www.healthline.com/rss/health-news |
| `hindustan_times` | Hindustan Times | en_IN | RSS(低维护) | ⚠️ 可达被拒 | ✅ 可用 | 5094ms* | - | https://www.hindustantimes.com/feeds/rss/editorials/rssfeed.xml |
| `oc_register` | Orange County Register | en_US | RSS(低维护) | ⚠️ 可达被拒 | ✅ 可用 | 2063ms* | - | https://www.ocregister.com/news/ |
| `science_based_medicine` | Science Based Medicine | en | RSS(低维护) | ⚠️ 可达被拒 | ✅ 可用 | 7875ms* | - | http://www.sciencebasedmedicine.org/?feed=rss2 |
| `seattle_times` | The Seattle Times | en_US | RSS(低维护) | ⚠️ 可达被拒 | ✅ 可用 | 4858ms* | - | https://www.seattletimes.com/seattle-news/feed/ |
| `staradvertiser` | Honolulu Star-Advertiser | en | RSS(低维护) | ⚠️ 可达被拒 | ✅ 可用 | 686ms* | - | http://www.staradvertiser.com/category/breaking-news/feed/ |
| `the_sun` | The Sun UK | en_GB | RSS(低维护) | ⚠️ 可达被拒 | ✅ 可用 | 5468ms* | - | http://www.thesun.co.uk/sol/homepage/news/rss |
| `unz` | The Unz Review | en_US | RSS(低维护) | ⚠️ 可达被拒 | ✅ 可用 | 33843ms* | - | https://www.unz.com/feed |
| `walrusmag` | The Walrus Mag | en | RSS(低维护) | ⚠️ 可达被拒 | ✅ 可用 | 5468ms* | - | https://thewalrus.ca/feed/ |
| `chicago_tribune` | Chicago Tribune | en_US | 自定义解析(高维护) | ⚠️ 可达被拒 | ✅ 可用 | 17655ms* | - | https://www.chicagotribune.com/ |
| `epw` | Economic and Political Weekly | en_IN | 自定义解析(高维护) | ⚠️ 可达被拒 | ✅ 可用 | 6734ms* | - | http://www.epw.in/ |
| `epw_magazine` | EPW Magazine | en_IN | 自定义解析(高维护) | ⚠️ 可达被拒 | ✅ 可用 | 6046ms* | - | https://www.epw.in/journal/epw-archive |
| `gkt` | General Knowledge Today | en_IN | 自定义解析(高维护) | ⚠️ 可达被拒 | ✅ 可用 | 5218ms* | - | https://www.gktoday.in/current-affairs/ |
| `mwjournal` | Microwave Journal | en | 自定义解析(高维护) | ⚠️ 可达被拒 | ✅ 可用 | 7327ms* | - | http://www.microwavejournal.com |
| `newsminute` | The News Minute | en_IN | 自定义解析(高维护) | ⚠️ 可达被拒 | ✅ 可用 | 1422ms* | - | https://www.thenewsminute.com/ |
| `pc_world` | PCWorld | en | 自定义解析(高维护) | ⚠️ 可达被拒 | ✅ 可用 | 4906ms* | - | https://www.pcworld.com/ |
| `psych` | Psychology Today | en | 自定义解析(高维护) | ⚠️ 可达被拒 | ✅ 可用 | 6218ms* | - | https://www.psychologytoday.com/us/magazine/archive |
| `the_baffler` | The Baffler | en | 自定义解析(高维护) | ⚠️ 可达被拒 | ✅ 可用 | 5889ms* | - | https://thebaffler.com/issues |
| `theindiaforum` | The India Forum | en_IN | 自定义解析(高维护) | ⚠️ 可达被拒 | ✅ 可用 | 6015ms* | - | https://www.theindiaforum.in/ |
| `tls_mag` | Times Literary Supplement | en_GB | 自定义解析(高维护) | ⚠️ 可达被拒 | ✅ 可用 | 6047ms* | - | https://www.the-tls.com/issues/current-issue/ |
| `TheMITPressReader` | The MIT Press Reader | en | RSS(低维护) | ⚠️ 可达被拒 | ⚠️ 可达被拒 | - | - | https://thereader.mitpress.mit.edu/feed/ |
| `azstarnet` | Arizona Daily Star | en | RSS(低维护) | ⚠️ 可达被拒 | ⚠️ 可达被拒 | - | 需订阅 | http://azstarnet.com/search/?f=rss&t=article&c=news/local&l=25&s=start_time&sd=desc |
| `big_oven` | BigOven | en | RSS(低维护) | ⚠️ 可达被拒 | ⚠️ 可达被拒 | - | 需订阅 | http://www.bigoven.com/rss/recentraves |
| `bq_prime` | BQ Prime | en_IN | RSS(低维护) | ⚠️ 可达被拒 | ⚠️ 可达被拒 | - | - | https://www.bqprime.com/stories.rss |
| `cacm` | ACM CACM Magazine | en | RSS(低维护) | ⚠️ 可达被拒 | ⚠️ 可达被拒 | - | - | https://cacm.acm.org/magazine.rss |
| `cosmos` | Cosmos Magazine | en_AU | RSS(低维护) | ⚠️ 可达被拒 | ⚠️ 可达被拒 | - | - | https://cosmosmagazine.com/feed |
| `dna` | DNA India | en_IN | RSS(低维护) | ⚠️ 可达被拒 | ⚠️ 可达被拒 | - | - | http://www.dnaindia.com/syndication/rss_topnews.xml |
| `gates_notes` | Gates Notes | en | RSS(低维护) | ⚠️ 可达被拒 | ⚠️ 可达被拒 | - | - | https://www.gatesnotes.com/rss |
| `india_legal_magazine` | India Legal Magazine | en_IN | RSS(低维护) | ⚠️ 可达被拒 | ⚠️ 可达被拒 | - | - | https://www.indialegallive.com/constitutional-law-news/courts-news/rss |
| `mdj` | Marietta Daily Journal | en | RSS(低维护) | ⚠️ 可达被拒 | ⚠️ 可达被拒 | - | - | http://www.mdjonline.com/search/?f=rss&amp;t=article&amp;c=news/local&amp;l=50&amp;s=start_time&amp;sd=desc |
| `medscape` | MedScape | en | RSS(低维护) | ⚠️ 可达被拒 | ⚠️ 可达被拒 | - | 需订阅 | http://www.medscape.com/cx/rssfeeds/2685.xml |
| `oakland_north` | Oakland North | en_US | RSS(低维护) | ⚠️ 可达被拒 | ⚠️ 可达被拒 | - | - | http://oaklandnorth.net/feed/ |
| `oxford_mail` | Oxford Mail | en_GB | RSS(低维护) | ⚠️ 可达被拒 | ⚠️ 可达被拒 | - | - | http://www.oxfordmail.co.uk/news/rss/ |
| `politico` | Politico | en | RSS(低维护) | ⚠️ 可达被拒 | ⚠️ 可达被拒 | - | - | http://www.politico.com/rss/politicopicks.xml |
| `pravda_ukraine` | Ukrainska Pravda | en_UK | RSS(低维护) | ⚠️ 可达被拒 | ⚠️ 可达被拒 | - | - | https://www.pravda.com.ua/eng/rss/ |
| `readers_digest` | Readers Digest | en | RSS(低维护) | ⚠️ 可达被拒 | ⚠️ 可达被拒 | - | - | http://www.rd.com/food/feed |
| `readersdigest_thehealthy` | The Healthy from Readers Digest | en | RSS(低维护) | ⚠️ 可达被拒 | ⚠️ 可达被拒 | - | - | https://www.thehealthy.com/allergies/feed |
| `sanjosemercurynews` | San Jose Mercury News | en_US | RSS(低维护) | ⚠️ 可达被拒 | ⚠️ 可达被拒 | - | - | http://www.mercurynews.com/feed/ |
| `st_louis_post_dispatch` | St Louis Post-Dispatch | en_US | RSS(低维护) | ⚠️ 可达被拒 | ⚠️ 可达被拒 | - | - | https://www.stltoday.com/search/?c=news%2Flocal*&d1=&d2=&s=start_time&sd=desc&l=50&f=rss&t=article |
| `times_of_malta` | Times of Malta | en | RSS(低维护) | ⚠️ 可达被拒 | ⚠️ 可达被拒 | - | - | http://www.timesofmalta.com/rss |
| `tulsaworld` | Tulsa World | en_US | RSS(低维护) | ⚠️ 可达被拒 | ⚠️ 可达被拒 | - | - | http://www.tulsaworld.com/search/?f=rss&t=article&c=news&l=150&s=start_time&sd=desc |
| `villagevoice` | Village Voice | en | RSS(低维护) | ⚠️ 可达被拒 | ⚠️ 可达被拒 | - | - | http://villagevoice.com/syndication/issue |
| `free_inquiry` | Free Inquiry | en | 自定义解析(高维护) | ⚠️ 可达被拒 | ⚠️ 可达被拒 | - | 需订阅 | https://secularhumanism.org/latest/ |
| `granta` | Granta | en | 自定义解析(高维护) | ⚠️ 可达被拒 | ⚠️ 可达被拒 | - | - | https://granta.com/issues |
| `history_today` | History Today | en | 自定义解析(高维护) | ⚠️ 可达被拒 | ⚠️ 可达被拒 | - | 需订阅 | https://www.historytoday.com/ |
| `johm` | Journal of Hospital Medicine | en | 自定义解析(高维护) | ⚠️ 可达被拒 | ⚠️ 可达被拒 | - | 需订阅 | http://onlinelibrary.wiley.com |
| `nejm` | New England Journal of Medicine | en | 自定义解析(高维护) | ⚠️ 可达被拒 | ⚠️ 可达被拒 | - | 需订阅 | https://www.nejm.org |
| `science_advances` | Science Advances | en | 自定义解析(高维护) | ⚠️ 可达被拒 | ⚠️ 可达被拒 | - | 需浏览器渲染 | https://www.science.org/toc/sciadv/current |
| `science_journal` | Science Journal | en | 自定义解析(高维护) | ⚠️ 可达被拒 | ⚠️ 可达被拒 | - | 需浏览器渲染 | https://www.science.org/toc/science/current |
| `sciimmunol` | Science Immunology | en | 自定义解析(高维护) | ⚠️ 可达被拒 | ⚠️ 可达被拒 | - | 需浏览器渲染 | https://www.science.org/toc/sciimmunol/current |
| `scirobotics` | Science Robotics | en | 自定义解析(高维护) | ⚠️ 可达被拒 | ⚠️ 可达被拒 | - | 需浏览器渲染 | https://www.science.org/toc/scirobotics/current |
| `scisignaling` | Science Signaling | en | 自定义解析(高维护) | ⚠️ 可达被拒 | ⚠️ 可达被拒 | - | 需浏览器渲染 | https://www.science.org/toc/signaling/current |
| `scistm` | Science Translational Medicine | en | 自定义解析(高维护) | ⚠️ 可达被拒 | ⚠️ 可达被拒 | - | 需浏览器渲染 | https://www.science.org/toc/stm/current |
| `todoist` | Todoist | ? | 自定义解析(高维护) | ⚠️ 可达被拒 | ⚠️ 可达被拒 | - | no_language | https://api.todoist.com/api/v1/tasks?project_id= |
| `usatoday` | USA Today | en_US | 自定义解析(高维护) | ⚠️ 可达被拒 | ⚠️ 可达被拒 | - | - | https://www.usatoday.com/ |
| `iol_za` | IOL News | en_ZA | RSS(低维护) | ❌ 404 | ❌ 404 | - | - | http://iol.co.za/cmlink/1.640 |
| `newsstraitstimes` | New Straits Times from Malaysia | en | RSS(低维护) | ❌ 404 | ❌ 404 | - | - | http://www.nst.com.my/latest.xml |
| `radio_prague` | Radio Praha | en_CZ | RSS(低维护) | ❌ 404 | ❌ 404 | - | - | http://www.radio.cz/feeds/rss/en/themes/curraffrs.xml |
| `natgeomag` | National Geographic Magazine | en | 自定义解析(高维护) | ❌ 404 | ❌ 404 | - | - | https://www.nationalgeographic.com/magazine/issue/ |
| `smith` | Smithsonian Magazine | en | 自定义解析(高维护) | ❌ 404 | ❌ 404 | - | - | https://www.smithsonianmag.com/category/ |
| `abc_au` | ABC News | en_AU | RSS(低维护) | ❌ 不可达 | ✅ 可用 | 1094ms* | - | https://www.abc.net.au/news/feed/45910/rss.xml |
| `al_jazeera` | Al Jazeera in English | en | RSS(低维护) | ❌ 不可达 | ✅ 可用 | 703ms* | - | http://www.aljazeera.com/xml/rss/all.xml |
| `albert_mohler` | Albert Mohler's Blog | en | RSS(低维护) | ❌ 不可达 | ✅ 可用 | 2110ms* | - | http://feeds.feedburner.com/AlbertMohlersBlog?format=xml |
| `american_thinker` | American Thinker | en_US | RSS(低维护) | ❌ 不可达 | ✅ 可用 | 1797ms* | - | http://feeds.feedburner.com/americanthinker |
| `android_police` | Android Police | en | RSS(低维护) | ❌ 不可达 | ✅ 可用 | 1327ms* | - | https://www.androidpolice.com/feed/phones/ |
| `ars_technica` | Ars Technica | en | RSS(低维护) | ❌ 不可达 | ✅ 可用 | 6155ms* | - | https://feeds.arstechnica.com/arstechnica/index |
| `asianreviewofbooks` | The Asian Review of Books | en_CN | RSS(低维护) | ❌ 不可达 | ✅ 可用 | 8983ms* | - | http://asianreviewofbooks.com/content/feed/ |
| `backyard_boss` | Backyard Boss | en | RSS(低维护) | ❌ 不可达 | ✅ 可用 | 6827ms* | - | https://www.backyardboss.net/feed/category/gardening/ |
| `baltimore_sun` | The Baltimore Sun | en_US | RSS(低维护) | ❌ 不可达 | ✅ 可用 | 1718ms* | - | http://feeds.feedburner.com/baltimoresun/news/rss2 |
| `bbc` | BBC News | en_GB | RSS(低维护) | ❌ 不可达 | ✅ 可用 | 1733ms* | - | https://feeds.bbci.co.uk/news/uk/rss.xml |
| `bbc_fast` | BBC News (fast) | en_GB | RSS(低维护) | ❌ 不可达 | ✅ 可用 | 1843ms* | - | https://feeds.bbci.co.uk/news/science_and_environment/rss.xml |
| `bbc_sport` | BBC Sport | en_GB | RSS(低维护) | ❌ 不可达 | ✅ 可用 | 4577ms* | - | http://newsrss.bbc.co.uk/rss/sportonline_uk_edition/front_page/rss.xml |
| `bellingcat_en` | Bellingcat | en | RSS(低维护) | ❌ 不可达 | ✅ 可用 | 1343ms* | - | https://www.bellingcat.com/feed/ |
| `biamag_en` | Bianet-English | en_TR | RSS(低维护) | ❌ 不可达 | ✅ 可用 | 3750ms* | - | http://www.bianet.org/english.rss |
| `big_picture` | The Big Picture | en | RSS(低维护) | ❌ 不可达 | ✅ 可用 | 1765ms* | - | http://feeds.feedburner.com/TheBigPicture |
| `biggovernment` | Big Government | en | RSS(低维护) | ❌ 不可达 | ✅ 可用 | 1360ms* | - | http://feeds.feedburner.com/breitbart |
| `business_insider` | Business Insider | en | RSS(低维护) | ❌ 不可达 | ✅ 可用 | 688ms* | - | http://feeds2.feedburner.com/businessinsider |
| `car_buzz` | Car Buzz | en | RSS(低维护) | ❌ 不可达 | ✅ 可用 | 6313ms* | - | https://carbuzz.com/feed/category/news/ |
| `catholic_news_agency` | Catholic News Agency | en | RSS(低维护) | ❌ 不可达 | ✅ 可用 | 969ms* | - | http://feeds.feedburner.com/catholicnewsagency/dailynews-us |
| `cato` | The CATO Institute | en | RSS(低维护) | ❌ 不可达 | ✅ 可用 | 10250ms* | - | http://feeds.cato.org/CatoRecentOpeds |
| `cbc_canada` | CBC Canada | en_CA | RSS(低维护) | ❌ 不可达 | ✅ 可用 | 6000ms* | - | https://www.cbc.ca/webfeed/rss/rss-topstories |
| `cbn` | CBN News | en | RSS(低维护) | ❌ 不可达 | ✅ 可用 | 15561ms* | - | http://www.cbn.com/cbnnews/world/feed/ |
| `chr_mon` | The Christian Science Monitor - daily | en | RSS(低维护) | ❌ 不可达 | ✅ 可用 | 6859ms* | - | http://rss.csmonitor.com/feeds/usa |
| `cnn` | CNN | en_US | RSS(低维护) | ❌ 不可达 | ✅ 可用 | 4813ms* | - | http://rss.cnn.com/rss/cnn_topstories.rss |
| `coda` | Coda | en_RU | RSS(低维护) | ❌ 不可达 | ✅ 可用 | 16733ms* | - | https://www.codastory.com/feed/ |
| `collider` | Collider | en | RSS(低维护) | ❌ 不可达 | ✅ 可用 | 6188ms* | - | https://collider.com/feed/category/movies/ |
| `comic_book_archive` | Comic Book Archive | en | RSS(低维护) | ❌ 不可达 | ✅ 可用 | 6468ms* | - | https://www.cbr.com/feed/tag/marvel/ |
| `computer_weekly` | ComputerWeekly | en | RSS(低维护) | ❌ 不可达 | ✅ 可用 | 7453ms* | - | https://www.computerweekly.com/rss/IT-careers-and-IT-skills.xml |
| `cracked_com` | Cracked.com | en | RSS(低维护) | ❌ 不可达 | ✅ 可用 | 1000ms* | - | http://feeds.feedburner.com/CrackedRSS/ |
| `crikey` | Crikey | en_AU | RSS(低维护) | ❌ 不可达 | ✅ 可用 | 6000ms* | 需订阅 | https://www.crikey.com.au/politics/feed |
| `daily_mail` | The Daily Mail | en_GB | RSS(低维护) | ❌ 不可达 | ✅ 可用 | 11906ms* | 需浏览器渲染 | https://www.dailymail.co.uk/home/index.rss |
| `daily_writing_tips` | Daily Writing Tips | en_GB | RSS(低维护) | ❌ 不可达 | ✅ 可用 | 718ms* | - | http://feeds2.feedburner.com/DailyWritingTips |
| `dailyreckoning` | The Daily Reckoning - US edition | en_US | RSS(低维护) | ❌ 不可达 | ✅ 可用 | 875ms* | - | http://feeds.feedburner.com/dailyreckoning?format=xml |
| `dark_horizons` | Dark Horizons | en | RSS(低维护) | ❌ 不可达 | ✅ 可用 | 9110ms* | - | http://www.darkhorizons.com/feeds/news.atom |
| `democracy_now` | Democracy now! | en | RSS(低维护) | ❌ 不可达 | ✅ 可用 | 17906ms* | - | http://www.democracynow.org/democracynow.rss |
| `desiring_god` | Desiring God | en | RSS(低维护) | ❌ 不可达 | ✅ 可用 | 1030ms* | - | http://feeds.feedburner.com/DGBlog?format=xml |
| `deutsche_welle_en` | Deutsche Welle | en | RSS(低维护) | ❌ 不可达 | ✅ 可用 | 6484ms* | - | http://rss.dw.de/rdf/rss-en-world |
| `dot_net` | Creative Blog | en | RSS(低维护) | ❌ 不可达 | ✅ 可用 | 6156ms* | - | http://www.creativebloq.com/feed/ |
| `dual_shockers` | Dual SHOCKERS | en | RSS(低维护) | ❌ 不可达 | ✅ 可用 | 5625ms* | - | https://www.dualshockers.com/feed/video-game-news/ |
| `en_globes_co_il` | Globes in English | en | RSS(低维护) | ❌ 不可达 | ✅ 可用 | 5905ms* | - | https://www.globes.co.il/WebService/Rss/RssFeeder.asmx/FeederNode?iID=942 |
| `epl_talk` | EPL Talk | en | RSS(低维护) | ❌ 不可达 | ✅ 可用 | 734ms* | - | http://feeds.feedburner.com/EPLTalk |
| `espn` | ESPN | en | RSS(低维护) | ❌ 不可达 | ✅ 可用 | 6047ms* | - | https://www.espn.com/espn/rss/news |
| `fan_graphs` | FanGraphs | en | RSS(低维护) | ❌ 不可达 | ✅ 可用 | 1000ms* | - | http://feeds.feedburner.com/FanGraphs?format=xml |
| `fastcompany` | Fast Company | en | RSS(低维护) | ❌ 不可达 | ✅ 可用 | 781ms* | - | http://feeds.feedburner.com/fastcompany/headlines |
| `financial_times` | Financial Times | en_GB | RSS(低维护) | ❌ 不可达 | ✅ 可用 | 5983ms* | - | https://www.ft.com/firstft?format=rss |
| `financialsense` | Financial Sense | en | RSS(低维护) | ❌ 不可达 | ✅ 可用 | 405ms* | - | http://feeds.feedburner.com/fso |
| `flickr` | Flickr Blog | en | RSS(低维护) | ❌ 不可达 | ✅ 可用 | 656ms* | - | http://feeds.feedburner.com/Flickrblog |
| `football_fancast` | Football Fancast | en | RSS(低维护) | ❌ 不可达 | ✅ 可用 | 6422ms* | - | https://www.footballfancast.com/feed/tag/euro-2024/ |
| `football_league_world` | Football League World | en_GB | RSS(低维护) | ❌ 不可达 | ✅ 可用 | 3485ms* | - | https://footballleagueworld.co.uk/feed/transfer-rumours/ |
| `futurismic` | Futurismic | en | RSS(低维护) | ❌ 不可达 | ✅ 可用 | 640ms* | - | http://feeds2.feedburner.com/futurismic_feed |
| `game_rant` | Game Rant | en | RSS(低维护) | ❌ 不可达 | ✅ 可用 | 5656ms* | - | https://gamerant.com/feed/gaming/ |
| `give_me_sports` | Give Me Sports | en | RSS(低维护) | ❌ 不可达 | ✅ 可用 | 5843ms* | - | https://www.givemesport.com/feed/tag/premier-league/ |
| `glenn_greenwald` | Glenn Greenwald | guardian.co.uk | en_GB | RSS(低维护) | ❌ 不可达 | ✅ 可用 | 16922ms* | - | http://www.guardian.co.uk/profile/glenn-greenwald/rss |
| `habr` | Habr | en_RU | RSS(低维护) | ❌ 不可达 | ✅ 可用 | 6827ms* | - | https://habr.com/en/rss/news/?fl=en |
| `hardcore_gamer` | Hardcore Gamer | en | RSS(低维护) | ❌ 不可达 | ✅ 可用 | 1281ms* | - | https://hardcoregamer.com/feed/category/news/ |
| `hindufeeds` | The Hindu News | en_IN | RSS(低维护) | ❌ 不可达 | ✅ 可用 | 4985ms* | - | https://www.thehindu.com/opinion/feeder/default.rss |
| `hot_cars` | Hot Cars | en | RSS(低维护) | ❌ 不可达 | ✅ 可用 | 6968ms* | - | https://www.hotcars.com/feed/category/fast-cars/ |
| `hotair` | Hot Air | en | RSS(低维护) | ❌ 不可达 | ✅ 可用 | 546ms* | - | http://feeds.feedburner.com/hotair/main |
| `howtogeek` | How-To Geek | en | RSS(低维护) | ❌ 不可达 | ✅ 可用 | 5875ms* | - | https://www.howtogeek.com/feed/category/desktop/ |
| `independent` | The Independent | en_GB | RSS(低维护) | ❌ 不可达 | ✅ 可用 | 5422ms* | - | http://www.independent.co.uk/news/uk/rss |
| `independent_australia` | Independent Australia | en_AU | RSS(低维护) | ❌ 不可达 | ✅ 可用 | 688ms* | - | https://feeds.feedburner.com/IndependentAustralia |
| `india_speaks_reddit` | IndiaSpeaksReddit | en_IN | RSS(低维护) | ❌ 不可达 | ✅ 可用 | 6061ms* | - | https://www.reddit.com/r/IndiaSpeaks.rss |
| `indic_today` | Indic Today | en_IN | RSS(低维护) | ❌ 不可达 | ✅ 可用 | 5640ms* | - | https://www.indica.today/feed/ |
| `inquirer_net` | Inquirer.net | en | RSS(低维护) | ❌ 不可达 | ✅ 可用 | 5438ms* | - | http://www.inquirer.net/fullfeed |
| `irish_independent` | Irish Independent | en_IE | RSS(低维护) | ❌ 不可达 | ✅ 可用 | 6156ms* | - | http://www.independent.ie/rss |
| `japan_news` | News On Japan | en | RSS(低维护) | ❌ 不可达 | ✅ 可用 | 4983ms* | - | http://newsonjapan.com/rss/top.xml |
| `japan_times` | The Japan Times | en_JP | RSS(低维护) | ❌ 不可达 | ✅ 可用 | 5671ms* | - | https://www.japantimes.co.jp/feed/topstories/ |
| `krebs_on_security` | Krebs on Security | en | RSS(低维护) | ❌ 不可达 | ✅ 可用 | 734ms* | - | http://feeds.feedburner.com/KrebsOnSecurity |
| `lamebook` | Lamebook | en | RSS(低维护) | ❌ 不可达 | ✅ 可用 | 890ms* | - | http://feeds.feedburner.com/Lamebook |
| `le_monde_en` | Le Monde in English | en | RSS(低维护) | ❌ 不可达 | ✅ 可用 | 5234ms* | - | https://www.lemonde.fr/en/rss/une.xml |
| `livemint` | Live Mint | en_IN | RSS(低维护) | ❌ 不可达 | ✅ 可用 | 5077ms* | - | https://www.livemint.com/rss/companies |
| `make_use_of` | Make Use of | en | RSS(低维护) | ❌ 不可达 | ✅ 可用 | 6719ms* | - | https://www.makeuseof.com/feed/category/pc-mobile/ |
| `merco_press` | Merco Press | en | RSS(低维护) | ❌ 不可达 | ✅ 可用 | 6452ms* | - | http://en.mercopress.com/rss/antarctica |
| `michellemalkin` | Michelle Malkin | en | RSS(低维护) | ❌ 不可达 | ✅ 可用 | 906ms* | - | http://feeds.feedburner.com/michellemalkin/posts |
| `motherjones` | Mother Jones | en | RSS(低维护) | ❌ 不可达 | ✅ 可用 | 1421ms* | - | http://feeds.feedburner.com/motherjones/feed |
| `national_post` | National Post | en_CA | RSS(低维护) | ❌ 不可达 | ✅ 可用 | 155ms* | - | http://nationalpost.com/rss |
| `navy_times` | Army and Navy Times | en | RSS(低维护) | ❌ 不可达 | ✅ 可用 | 6234ms* | - | https://www.navytimes.com/arc/outboundfeeds/rss/?outputType=xml |
| `neowin` | Neowin.net | en | RSS(低维护) | ❌ 不可达 | ✅ 可用 | 7827ms* | - | http://www.neowin.net/news/rss/software |
| `novinite` | Novinite.com | en_BG | RSS(低维护) | ❌ 不可达 | ✅ 可用 | 2000ms* | - | http://www.novinite.com/services/news_rdf.php?category_id=1 |
| `nspm_int` | NSPM in English | en | RSS(低维护) | ❌ 不可达 | ✅ 可用 | 6561ms* | - | http://www.nspm.rs/nspm-in-english/feed/rss.html |
| `nypost` | New York Post | en_US | RSS(低维护) | ❌ 不可达 | ✅ 可用 | 10250ms* | - | https://nypost.com/us-news/feed/ |
| `nyt_magazine` | NYT Magazine | en_US | RSS(低维护) | ❌ 不可达 | ✅ 可用 | 6936ms* | 需浏览器渲染 | https://rss.nytimes.com/services/xml/rss/nyt/Magazine.xml |
| `nyt_tmag` | NYT T Magazine | en_US | RSS(低维护) | ❌ 不可达 | ✅ 可用 | 16766ms* | 需浏览器渲染 | https://rss.nytimes.com/services/xml/rss/nyt/tmagazine.xml |
| `nytfeeds` | NYT News | en_US | RSS(低维护) | ❌ 不可达 | ✅ 可用 | 15906ms* | - | https://rss.nytimes.com/services/xml/rss/nyt/Opinion.xml |
| `nytimes_sports` | New York Times Sports Beat | en_US | RSS(低维护) | ❌ 不可达 | ✅ 可用 | 6656ms* | - | https://fifthdown.blogs.nytimes.com/feed/ |
| `nytimes_tech` | New York Times Technology Beat | en_US | RSS(低维护) | ❌ 不可达 | ✅ 可用 | 16155ms* | - | https://rss.nytimes.com/services/xml/rss/nyt/Technology.xml |
| `opindia` | opindia | en_IN | RSS(低维护) | ❌ 不可达 | ✅ 可用 | 1250ms* | - | https://feeds.feedburner.com/opindia |
| `ourdailybread` | Our Daily Bread | en | RSS(低维护) | ❌ 不可达 | ✅ 可用 | 14593ms* | - | http://odb.org/feed/ |
| `pajama` | Pajamas Media | en | RSS(低维护) | ❌ 不可达 | ✅ 可用 | 1031ms* | - | http://feeds.feedburner.com/PajamasMedia |
| `parisreview` | The Paris Review Blog | en | RSS(低维护) | ❌ 不可达 | ✅ 可用 | 609ms* | - | http://feeds.feedburner.com/TheParisReviewBlog |
| `pocket_lint` | Pocket-lint | en | RSS(低维护) | ❌ 不可达 | ✅ 可用 | 5625ms* | - | https://www.pocket-lint.com/feed/devices-segment/ |
| `poliitico_eu` | Politico.eu | en | RSS(低维护) | ❌ 不可达 | ✅ 可用 | 6609ms* | - | https://www.politico.eu/section/policy/feed |
| `project_syndicate` | Project Syndicate | en | RSS(低维护) | ❌ 不可达 | ✅ 可用 | 1734ms* | - | https://www.project-syndicate.org/rss/section/economics |
| `rushisaband` | Rushisaband | en_GB | RSS(低维护) | ❌ 不可达 | ✅ 可用 | 688ms* | - | http://feeds2.feedburner.com/rushisaband/blog |
| `scmp` | South China Morning Post | en_HK | RSS(低维护) | ❌ 不可达 | ✅ 可用 | 6468ms* | - | https://www.scmp.com/rss/2/feed |
| `scott_hanselman` | Scott Hanselman's Computer Zen | en | RSS(低维护) | ❌ 不可达 | ✅ 可用 | 7108ms* | - | http://feeds2.feedburner.com/ScottHanselman |
| `screen_rant` | Screen Rant | en | RSS(低维护) | ❌ 不可达 | ✅ 可用 | 6422ms* | - | https://screenrant.com/feed/movies/ |
| `skeptic` | The Skeptic | en | RSS(低维护) | ❌ 不可达 | ✅ 可用 | 1093ms* | - | http://feeds.feedburner.com/Skepticcom |
| `smh` | The Sydney Morning Herald | en_AU | RSS(低维护) | ❌ 不可达 | ✅ 可用 | 6718ms* | - | https://www.smh.com.au/rss/feed.xml |
| `spectator-au` | Spectator Australia | en_AU | RSS(低维护) | ❌ 不可达 | ✅ 可用 | 3797ms* | - | https://www.spectator.com.au/feed/ |
| `spiegel_int` | Spiegel Online International | en_DE | RSS(低维护) | ❌ 不可达 | ✅ 可用 | 5640ms* | - | https://www.spiegel.de/international/world/index.rss |
| `straitstimes` | The Straits Times | en_SG | RSS(低维护) | ❌ 不可达 | ✅ 可用 | 6484ms* | - | https://www.straitstimes.com/news/world/rss.xml |
| `techdirt` | Tech Dirt | en | RSS(低维护) | ❌ 不可达 | ✅ 可用 | 671ms* | - | http://feeds.feedburner.com/techdirt/feed |
| `tehelka` | Tehelka | en_IN | RSS(低维护) | ❌ 不可达 | ✅ 可用 | 7375ms* | - | http://tehelka.com/rss |
| `the_budget_fashionista` | The Budget Fashionista | en | RSS(低维护) | ❌ 不可达 | ✅ 可用 | 546ms* | - | http://feeds.feedburner.com/TheBudgetFashionista |
| `the_conversation` | The Conversation | en | RSS(低维护) | ❌ 不可达 | ✅ 可用 | 2686ms* | - | https://theconversation.com/au/articles.atom |
| `the_diplomat` | The Diplomat | en | RSS(低维护) | ❌ 不可达 | ✅ 可用 | 7733ms* | - | https://thediplomat.com/category/features/feed |
| `the_federalist` | The Federalist | en | RSS(低维护) | ❌ 不可达 | ✅ 可用 | 6156ms* | - | https://thefederalist.com/feed/ |
| `the_gamer` | The Gamer | en | RSS(低维护) | ❌ 不可达 | ✅ 可用 | 7890ms* | - | https://www.thegamer.com/feed/category/game-guides/ |
| `the_oz` | The Australian | en_AU | RSS(低维护) | ❌ 不可达 | ✅ 可用 | 6311ms* | - | https://www.news.com.au/content-feeds/latest-news-national/ |
| `the_philippine_daily_inquirer` | The Philippine Daily Inquirer | en_PH | RSS(低维护) | ❌ 不可达 | ✅ 可用 | 12765ms* | - | http://newsinfo.inquirer.net/category/inquirer-headlines/feed |
| `the_richest` | The Richest | en | RSS(低维护) | ❌ 不可达 | ✅ 可用 | 6500ms* | - | https://www.therichest.com/feed/category/rich-powerful/ |
| `the_sportster` | The Sportster | en | RSS(低维护) | ❌ 不可达 | ✅ 可用 | 6359ms* | - | https://www.thesportster.com/feed/category/wwe/ |
| `the_things` | The Things | en | RSS(低维护) | ❌ 不可达 | ✅ 可用 | 6796ms* | - | https://www.thethings.com/feed/category/celebrity/ |
| `the_travel` | The Travel | en | RSS(低维护) | ❌ 不可达 | ✅ 可用 | 2452ms* | - | https://www.thetravel.com/feed/category/travel-guides/ |
| `thedailywtf` | The Daily WTF | en | RSS(低维护) | ❌ 不可达 | ✅ 可用 | 6594ms* | - | http://syndication.thedailywtf.com/TheDailyWtf |
| `theeconomictimes_india` | The Economic Times India | en_IN | RSS(低维护) | ❌ 不可达 | ✅ 可用 | 6063ms* | - | http://economictimes.indiatimes.com/rssfeedstopstories.cms |
| `theonlinephotographer` | The Online Photographer | en | RSS(低维护) | ❌ 不可达 | ✅ 可用 | 531ms* | - | http://feeds.feedburner.com/typepad/ZSjz |
| `top_speed` | Top Speed | en | RSS(低维护) | ❌ 不可达 | ✅ 可用 | 6014ms* | - | https://www.topspeed.com/feed/category/car-news/ |
| `truthout` | Truthout | en | RSS(低维护) | ❌ 不可达 | ✅ 可用 | 6797ms* | - | http://truthout.org/feed?format=feed |
| `uncrate` | Uncrate | en | RSS(低维护) | ❌ 不可达 | ✅ 可用 | 531ms* | - | http://feeds.feedburner.com/uncrate |
| `united_nations` | United Nations | en | RSS(低维护) | ❌ 不可达 | ✅ 可用 | 5436ms* | - | https://news.un.org/feed/subscribe/en/news/all/rss.xml |
| `universe_today` | Universe Today | en | RSS(低维护) | ❌ 不可达 | ✅ 可用 | 6640ms* | - | http://feeds.feedburner.com/universetoday/pYdq |
| `variety` | Variety | en | RSS(低维护) | ❌ 不可达 | ✅ 可用 | 968ms* | - | http://feeds.feedburner.com/variety/headlines |
| `vox` | VOX | en | RSS(低维护) | ❌ 不可达 | ✅ 可用 | 1141ms* | - | https://www.vox.com/rss/index.xml |
| `winnipeg_free_press` | Winnipeg Free Press | en_CA | RSS(低维护) | ❌ 不可达 | ✅ 可用 | 28391ms* | - | http://www.winnipegfreepress.com/rss?path=/breakingnews |
| `xda` | XDA | en | RSS(低维护) | ❌ 不可达 | ✅ 可用 | 5922ms* | - | https://www.xda-developers.com/feed/news/ |
| `yahoo_news` | Yahoo News | en | RSS(低维护) | ❌ 不可达 | ✅ 可用 | 6343ms* | - | http://rss.news.yahoo.com/rss/topstories |
| `zerohedge` | Zero Hedge | en | RSS(低维护) | ❌ 不可达 | ✅ 可用 | 547ms* | - | http://feeds.feedburner.com/zerohedge/feed |
| `afr` | Australian Financial Review | en_AU | 自定义解析(高维护) | ❌ 不可达 | ✅ 可用 | 1969ms* | - | https://www.afr.com/ |
| `amspec` | The American Spectator | en_US | 自定义解析(高维护) | ❌ 不可达 | ✅ 可用 | 3516ms* | - | http://spectator.org/issues/current |
| `bloomberg` | Bloomberg | en_US | 自定义解析(高维护) | ❌ 不可达 | ✅ 可用 | 921ms* | - | https://cdn-mobapi.bloomberg.com |
| `bloomberg-business-week` | Bloomberg Businessweek | en_US | 自定义解析(高维护) | ❌ 不可达 | ✅ 可用 | 5406ms* | - | https://cdn-mobapi.bloomberg.com |
| `deccan_herald` | Deccan Herald | en_IN | 自定义解析(高维护) | ❌ 不可达 | ✅ 可用 | 5000ms* | - | https://www.deccanherald.com/ |
| `equestria_daily` | Equestria Daily | en | 自定义解析(高维护) | ❌ 不可达 | ✅ 可用 | 6015ms* | - | https://www.equestriadaily.com/search/label/ |
| `first_things` | First Things | en | 自定义解析(高维护) | ❌ 不可达 | ✅ 可用 | 11327ms* | - | https://www.firstthings.com/ |
| `foreign_policy` | Foreign Policy | en | 自定义解析(高维护) | ❌ 不可达 | ✅ 可用 | 5750ms* | - | https://foreignpolicy.com/the-magazine |
| `foreignaffairs` | Foreign Affairs | en | 自定义解析(高维护) | ❌ 不可达 | ✅ 可用 | 2438ms* | - | https://www.foreignaffairs.com/issues/ |
| `frontline` | Frontline | en_IN | 自定义解析(高维护) | ❌ 不可达 | ✅ 可用 | 5250ms* | - | https://frontline.thehindu.com/current-issue/ |
| `guardian` | The Guardian and The Observer | en_GB | 自定义解析(高维护) | ❌ 不可达 | ✅ 可用 | 5578ms* | - | https://www.theguardian.com/uk/sport |
| `hamilton_spectator` | Hamilton Spectator | en_CA | 自定义解析(高维护) | ❌ 不可达 | ✅ 可用 | 1485ms* | - | https://www.thespec.com/ |
| `india_today` | India Today Magazine | en_IN | 自定义解析(高维护) | ❌ 不可达 | ✅ 可用 | 5250ms* | - | https://www.indiatoday.in/magazine |
| `jacobinmag` | Jacobin | en | 自定义解析(高维护) | ❌ 不可达 | ✅ 可用 | 13328ms* | - | https://www.jacobinmag.com/store/issues |
| `jakarta_post` | Jakarta Post | en_ID | 自定义解析(高维护) | ❌ 不可达 | ✅ 可用 | 5500ms* | - | http://www.thejakartapost.com/ |
| `kirkusreviews` | Kirkus Reviews | en | 自定义解析(高维护) | ❌ 不可达 | ✅ 可用 | 6468ms* | - | https://www.kirkusreviews.com/magazine/current/ |
| `mainichi_en` | The Mainichi | en_JP | 自定义解析(高维护) | ❌ 不可达 | ✅ 可用 | 891ms* | - | https://mainichi.jp/english/ |
| `new_york_review_of_books` | New York Review of Books | en_US | 自定义解析(高维护) | ❌ 不可达 | ✅ 可用 | 6406ms* | 需订阅 | https://www.nybooks.com/current-issue |
| `new_york_review_of_books_no_sub` | New York Review of Books (no subscription) | en_US | 自定义解析(高维护) | ❌ 不可达 | ✅ 可用 | 6436ms* | - | https://www.nybooks.com/current-issue |
| `newrepublicmag` | The New Republic Magazine | en | 自定义解析(高维护) | ❌ 不可达 | ✅ 可用 | 6500ms* | - | https://newrepublic.com/api/content/magazine |
| `nytimes_cooking` | NY Times Cooking | en_US | 自定义解析(高维护) | ❌ 不可达 | ✅ 可用 | 3219ms* | - | https://cooking.nytimes.com/topics/what-to-cook-this-week |
| `nytimesbook` | New York Times Book Review | en_US | 自定义解析(高维护) | ❌ 不可达 | ✅ 可用 | 7186ms* | - | https://www.nytimes.com/pages/books/review/index.html |
| `orfonline` | Observer Research Foundation | en_IN | 自定义解析(高维护) | ❌ 不可达 | ✅ 可用 | 5235ms* | - | https://www.orfonline.org |
| `outlook_india` | Outlook Magazine | en_IN | 自定义解析(高维护) | ❌ 不可达 | ✅ 可用 | 750ms* | 需浏览器渲染 | https://www.outlookindia.com/magazine/ |
| `press_information_bureau` | Press Information Bureau | en_IN | 自定义解析(高维护) | ❌ 不可达 | ✅ 可用 | 3280ms* | - | https://pib.gov.in/Allrel.aspx |
| `spectator_magazine` | Spectator Magazine | en_GB | 自定义解析(高维护) | ❌ 不可达 | ✅ 可用 | 10030ms* | - | https://www.spectator.co.uk/magazine |
| `sports_illustrated` | Sports Illustrated | en | 自定义解析(高维护) | ❌ 不可达 | ✅ 可用 | 6406ms* | - | https://www.si.com/ |
| `sportstar` | Sportstar | en_IN | 自定义解析(高维护) | ❌ 不可达 | ✅ 可用 | 7766ms* | - | https://sportstar.thehindu.com/magazine/issue/vol |
| `swarajya` | Swarajya Magazine | en_IN | 自定义解析(高维护) | ❌ 不可达 | ✅ 可用 | 5436ms* | - | https://swarajyamag.com/all-issues |
| `the_age` | The Age | en_AU | 自定义解析(高维护) | ❌ 不可达 | ✅ 可用 | 8438ms* | - | http://www.theage.com.au/text/ |
| `the_saturday_paper` | The Saturday Paper | en_AU | 自定义解析(高维护) | ❌ 不可达 | ✅ 可用 | 7905ms* | - | https://www.thesaturdaypaper.com.au/news |
| `the_week` | The Week | en_IN | 自定义解析(高维护) | ❌ 不可达 | ✅ 可用 | 2219ms* | - | https://www.theweek.in/theweek/ |
| `the_week_magazine_free` | The Week | en_US | 自定义解析(高维护) | ❌ 不可达 | ✅ 可用 | 4280ms* | - | https://theweek.com/archive |
| `the_week_uk` | The Week | en_GB | 自定义解析(高维护) | ❌ 不可达 | ✅ 可用 | 7109ms* | - | https://theweek.com/archive |
| `the_wire` | The Wire | en_IN | 自定义解析(高维护) | ❌ 不可达 | ✅ 可用 | 6781ms* | - | https://thewirehindi.com/home_data_2.json |
| `theeconomictimes_india_print_edition` | The Economic Times | Print Edition | en_IN | 自定义解析(高维护) | ❌ 不可达 | ✅ 可用 | 8703ms* | - | https://economictimes.indiatimes.com/print_edition.cms |
| `thestar` | The Toronto Star | en_CA | 自定义解析(高维护) | ❌ 不可达 | ✅ 可用 | 5952ms* | - | https://www.thestar.com/ |
| `toiprint` | TOI Print Edition | en_IN | 自定义解析(高维护) | ❌ 不可达 | ✅ 可用 | 6078ms* | - | https://epaper.indiatimes.com/english-news-paper-today-toi-print-edition/ |
| `business_standard` | Business Standard | en_IN | RSS(低维护) | ❌ 不可达 | ⚠️ 可达被拒 | - | 需浏览器渲染 | https://www.business-standard.com/rss/home_page_top_stories.rss |
| `indian_express` | Indian Express | en_IN | RSS(低维护) | ❌ 不可达 | ⚠️ 可达被拒 | - | - | https://indianexpress.com/section/opinion/feed |
| `kstar` | Kansas City Star | en_US | RSS(低维护) | ❌ 不可达 | ⚠️ 可达被拒 | - | - | http://www.kansascity.com/?widgetName=rssfeed&widgetContentId=6199&getXmlFeed=true |
| `miami_herald` | The Miami Herald | en_US | RSS(低维护) | ❌ 不可达 | ⚠️ 可达被拒 | - | - | https://www.miamiherald.com/news/?widgetName=rssfeed&widgetContentId=712015&getXmlFeed=true |
| `new_statesman` | New Statesman | en_GB | RSS(低维护) | ❌ 不可达 | ⚠️ 可达被拒 | - | - | https://www.newstatesman.com/feed |
| `t_invariant_en` | T-Invariant | en_RU | RSS(低维护) | ❌ 不可达 | ⚠️ 可达被拒 | - | - | https://tinyurl.com/t-invariant/en/feed/ |
| `1843` | The Economist 1843 | en_GB | 自定义解析(高维护) | ❌ 不可达 | ⚠️ 可达被拒 | - | 需浏览器渲染 | https://www.economist.com/1843 |
| `business_standard_print` | Business Standard Print Edition | en_IN | 自定义解析(高维护) | ❌ 不可达 | ⚠️ 可达被拒 | - | 需浏览器渲染 | https://apibs.business-standard.com/category/today-paper?sortBy= |
| `economist` | The Economist | en_GB | 自定义解析(高维护) | ❌ 不可达 | ⚠️ 可达被拒 | - | 需浏览器渲染 | https://www.economist.com/weeklyedition |
| `economist_espresso` | The Economist Espresso | en_GB | 自定义解析(高维护) | ❌ 不可达 | ⚠️ 可达被拒 | - | 需浏览器渲染 | https://www.economist.com/the-world-in-brief |
| `economist_free` | The Economist | en_GB | 自定义解析(高维护) | ❌ 不可达 | ⚠️ 可达被拒 | - | 需浏览器渲染 | https://www.economist.com/weeklyedition |
| `economist_news` | The Economist News | en_GB | 自定义解析(高维护) | ❌ 不可达 | ⚠️ 可达被拒 | - | 需浏览器渲染 | https://cp2-graphql-gateway.p.aws.economist.com/graphql? |
| `economist_world_ahead` | The Economist World Ahead | en_GB | 自定义解析(高维护) | ❌ 不可达 | ⚠️ 可达被拒 | - | 需浏览器渲染 | https://www.economist.com/the-world-ahead |
| `reuters` | Reuters | en | 自定义解析(高维护) | ❌ 不可达 | ⚠️ 可达被拒 | - | - | https://www.reuters.com |
| `thenewcriterion` | The New Criterion | en | 自定义解析(高维护) | ❌ 不可达 | ⚠️ 可达被拒 | - | - | https://newcriterion.com/issues/ |
| `theprint` | The Print | en_IN | 自定义解析(高维护) | ❌ 不可达 | ⚠️ 可达被拒 | - | - | https://theprint.in/ |
| `wsj` | The Wall Street Journal | en_US | 自定义解析(高维护) | ❌ 不可达 | ⚠️ 可达被拒 | - | - | https://shared-data.dowjones.io/gateway/graphql? |
| `wsj_free` | The Wall Street Journal | en_US | 自定义解析(高维护) | ❌ 不可达 | ⚠️ 可达被拒 | - | - | https://shared-data.dowjones.io/gateway/graphql? |
| `wsj_mag` | WSJ. Magazine | en_US | 自定义解析(高维护) | ❌ 不可达 | ⚠️ 可达被拒 | - | - | https://shared-data.dowjones.io/gateway/graphql? |
| `christian_post` | The Christian Post | en | RSS(低维护) | ❌ 不可达 | ❌ 404 | - | - | http://www.christianpost.com/services/rss/feed/ |
| `movie_web` | Movie Web | en_US | RSS(低维护) | ❌ 不可达 | ❌ 404 | - | - | https://movieweb.com/feed/trailers/ |
| `msnbc` | MSNBC | en_US | RSS(低维护) | ❌ 不可达 | ❌ 404 | - | - | https://feeds.nbcnews.com/msnbc/public/news |
| `open_magazine` | Open Magazine | en_IN | RSS(低维护) | ❌ 不可达 | ❌ 404 | - | - | https://openthemagazine.com/cover-story/feed/ |
| `words_without_borders` | Words Without Borders | en | RSS(低维护) | ❌ 不可达 | ❌ 404 | - | - | http://feeds.feedburner.com/wwborders?format=xml |
| `lrb` | London Review of Books | en_GB | 自定义解析(高维护) | ❌ 不可达 | ❌ 404 | - | - | https://www.lrb.co.uk/storage/800_filter/images/ |
| `the_monthly` | The Monthly | en_AU | 自定义解析(高维护) | ❌ 不可达 | ❌ 404 | - | - | https://www.themonthly.com.au/latest-edition |
| `cincinnati_enquirer` | Cincinnati Enquirer | en_US | RSS(低维护) | ❌ 不可达 | ❌ 不可达 | - | - | http://rss.cincinnati.com/apps/pbcs.dll/section?category=rssenq01&mime=xml |
| `epoch_times` | The Epoch Times | en | RSS(低维护) | ❌ 不可达 | ❌ 不可达 | - | - | https://feed.theepochtimes.com/health/special-series/feed |
| `javalobby` | Javalobby | en | RSS(低维护) | ❌ 不可达 | ❌ 不可达 | - | - | http://feeds.dzone.com/javalobby/frontpage |
| `news24` | News24 | en_ZA | RSS(低维护) | ❌ 不可达 | ❌ 不可达 | - | - | http://feeds.news24.com/articles/news24/TopStories/rss |
| `simple_flying` | Simple Flying | en | RSS(低维护) | ❌ 不可达 | ❌ 不可达 | - | - | https://simpleflying.com/feed/category/analysis/ |
| `sputnik` | Sputnik News | en_RU | RSS(低维护) | ❌ 不可达 | ❌ 不可达 | - | - | https://sputnikglobe.com/export/rss2/archive/index.xml |
| `toi` | The Times of India | en_IN | RSS(低维护) | ❌ 不可达 | ❌ 不可达 | - | - | http://timesofindia.indiatimes.com/rssfeeds/1221656.cms |
| `usnews` | US & World Report news | en_US | RSS(低维护) | ❌ 不可达 | ❌ 不可达 | - | - | http://www.usnews.com/rss/usnews.rss |
| `wash_post` | The Washington Post | en_US | RSS(低维护) | ❌ 不可达 | ❌ 不可达 | - | - | http://feeds.washingtonpost.com/rss/politics |
| `worldcrunch` | Worldcrunch | en | RSS(低维护) | ❌ 不可达 | ❌ 不可达 | - | - | http://www.worldcrunch.com/rss/rss.php |
| `frieze` | Frieze Magazine | en_GB | 自定义解析(高维护) | ❌ 不可达 | ❌ 不可达 | - | - | http |
| `go_comics` | Go Comics | en | 自定义解析(高维护) | ❌ 不可达 | ❌ 不可达 | - | - | https://www.gocomics.com/ |
| `private_eye` | Private Eye (Online Edition) | en_GB | 自定义解析(高维护) | ❌ 不可达 | ❌ 不可达 | - | - | http |
| `time_magazine` | TIME Magazine | en | 自定义解析(高维护) | ❌ 不可达 | ❌ 不可达 | - | - | http |
| `wash_post_print` | The Washington Post | Print Edition | en_US | 自定义解析(高维护) | ❌ 不可达 | ❌ 不可达 | - | - | https://www.washingtonpost.com/todays_paper/updates/ |
| `wsj_news` | WSJ News | en_US | 自定义解析(高维护) | ❌ 不可达 | ❌ 不可达 | - | - | https://bartender.mobile.dowjones.io |
| `DrawAndCook` | DrawAndCook | en | 自定义解析(高维护) | ❌ 不可达 | http_502 | - | - | http://www.theydrawandcook.com/ |
| `barrons` | Barron's Magazine | en_US | 自定义解析(高维护) | ❌ 不可达 | http_400 | - | - | https://barrons.djmedia.djservices.io |
| `nikkeiasia` | Nikkei Asia Magazine | en | 自定义解析(高维护) | ❌ 不可达 | http_500 | - | - | https://asia.nikkei.com/Print-Edition/Issue- |
| `ainonline` | Aviation International News | en | 自定义解析(高维护) | ➖ 未测试 | ➖ 未测试 | - | - | - |
| `al_monitor` | Al Monitor | en | 自定义解析(高维护) | ➖ 未测试 | ➖ 未测试 | - | - | - |
| `ald` | Arts and Letters Daily | en | 自定义解析(高维护) | ➖ 未测试 | ➖ 未测试 | - | - | - |
| `aprospect` | American Prospect | en_US | 未知 | ➖ 未测试 | ➖ 未测试 | - | - | - |
| `asahi_shimbun_en` | The Asahi Shimbun | en_JP | 自定义解析(高维护) | ➖ 未测试 | ➖ 未测试 | - | - | - |
| `atlantic` | atlantic | en | 未知 | ➖ 未测试 | ➖ 未测试 | - | - | - |
| `atlantic_com` | atlantic_com | en | 未知 | ➖ 未测试 | ➖ 未测试 | - | - | - |
| `bangkokpost` | Bangkok Post | en_TH | 未知 | ➖ 未测试 | ➖ 未测试 | - | - | - |
| `boston.com` | Boston Globe | en_US | 自定义解析(高维护) | ➖ 未测试 | ➖ 未测试 | - | - | - |
| `calgary_herald` | Calgary Herald | en_CA | 自定义解析(高维护) | ➖ 未测试 | ➖ 未测试 | - | - | - |
| `dawn` | Dawn | en_PK | 未知 | ➖ 未测试 | ➖ 未测试 | - | - | - |
| `economist_search` | The Economist - Search | en_GB | 自定义解析(高维护) | ➖ 未测试 | ➖ 未测试 | - | 需浏览器渲染 | - |
| `edmonton_journal` | Edmonton Journal | en_CA | 自定义解析(高维护) | ➖ 未测试 | ➖ 未测试 | - | - | - |
| `entrepeneur` | Entrepeneur Magazine | en | 自定义解析(高维护) | ➖ 未测试 | ➖ 未测试 | - | - | - |
| `factcheck` | Factcheck | en | 未知 | ➖ 未测试 | ➖ 未测试 | - | - | - |
| `firstpost` | Firstpost | en_IN | 未知 | ➖ 未测试 | ➖ 未测试 | - | - | - |
| `globaltimes` | Global Times | en_CN | 自定义解析(高维护) | ➖ 未测试 | ➖ 未测试 | - | - | - |
| `globe_and_mail` | The Globe and Mail | en_CA | 自定义解析(高维护) | ➖ 未测试 | ➖ 未测试 | - | - | - |
| `google_news` | Google News | ? | 未知 | ➖ 未测试 | ➖ 未测试 | - | no_language | - |
| `grantland` | Grantland | en | 自定义解析(高维护) | ➖ 未测试 | ➖ 未测试 | - | - | - |
| `hindu` | The Hindu Print Edition | en_IN | 自定义解析(高维护) | ➖ 未测试 | ➖ 未测试 | - | - | - |
| `hindu_business_line` | The Hindu BusinessLine | en_IN | 自定义解析(高维护) | ➖ 未测试 | ➖ 未测试 | - | - | - |
| `hindustan_times_print` | Hindustan Times Print Edition | en_IN | 自定义解析(高维护) | ➖ 未测试 | ➖ 未测试 | - | - | - |
| `ieee_spectrum_mag` | IEEE Spectrum Magazine | en | 未知 | ➖ 未测试 | ➖ 未测试 | - | - | - |
| `inc` | Inc Magazine | en | 自定义解析(高维护) | ➖ 未测试 | ➖ 未测试 | - | 需订阅 | - |
| `lemonde_dip` | Le Monde diplomatique - English edition | en | 自定义解析(高维护) | ➖ 未测试 | ➖ 未测试 | - | 需订阅 | - |
| `lwn` | lwn | en | 未知 | ➖ 未测试 | ➖ 未测试 | - | 需订阅 | - |
| `montreal_gazette` | Montreal Gazette | en_CA | 自定义解析(高维护) | ➖ 未测试 | ➖ 未测试 | - | - | - |
| `nature` | Nature | en | 自定义解析(高维护) | ➖ 未测试 | ➖ 未测试 | - | - | - |
| `new_yorker` | new_yorker | en_US | 未知 | ➖ 未测试 | ➖ 未测试 | - | - | - |
| `new_yorker_com` | new_yorker_com | en_US | 未知 | ➖ 未测试 | ➖ 未测试 | - | - | - |
| `nymag` | New York Magazine | en_US | 自定义解析(高维护) | ➖ 未测试 | ➖ 未测试 | - | - | - |
| `nytimes` | nytimes | en_US | 自定义解析(高维护) | ➖ 未测试 | ➖ 未测试 | - | 需浏览器渲染 | - |
| `nytimes_sub` | nytimes_sub | en_US | 自定义解析(高维护) | ➖ 未测试 | ➖ 未测试 | - | 需浏览器渲染 | - |
| `observer_gb` | The Observer | en_GB | 自定义解析(高维护) | ➖ 未测试 | ➖ 未测试 | - | - | - |
| `ottawa_citizen` | Ottawa Citizen | en_CA | 自定义解析(高维护) | ➖ 未测试 | ➖ 未测试 | - | - | - |
| `popscience` | Popular Science | en | 自定义解析(高维护) | ➖ 未测试 | ➖ 未测试 | - | - | - |
| `prospectmaguk_free` | Prospect Magazine (Free) | en_GB | 自定义解析(高维护) | ➖ 未测试 | ➖ 未测试 | - | - | - |
| `real_clear` | Real Clear | en | 自定义解析(高维护) | ➖ 未测试 | ➖ 未测试 | - | - | - |
| `reason_magazine` | Reason | en | 自定义解析(高维护) | ➖ 未测试 | ➖ 未测试 | - | 需订阅 | - |
| `regina_leader_post` | Regina Leader-Post | en_CA | 自定义解析(高维护) | ➖ 未测试 | ➖ 未测试 | - | - | - |
| `saskatoon_star_phoenix` | Saskatoon Star-Phoenix | en_CA | 自定义解析(高维护) | ➖ 未测试 | ➖ 未测试 | - | - | - |
| `satmagazine` | SatMagazine | en | 自定义解析(高维护) | ➖ 未测试 | ➖ 未测试 | - | - | - |
| `scprint` | SC Print Magazine | en | 自定义解析(高维护) | ➖ 未测试 | ➖ 未测试 | - | 需订阅 | - |
| `slate` | Slate | en | 自定义解析(高维护) | ➖ 未测试 | ➖ 未测试 | - | - | - |
| `substack` | Substack | en | 未知 | ➖ 未测试 | ➖ 未测试 | - | - | - |
| `theoldie` | theoldie | en_GB | 自定义解析(高维护) | ➖ 未测试 | ➖ 未测试 | - | - | - |
| `upi` | United Press International | en | 未知 | ➖ 未测试 | ➖ 未测试 | - | - | - |
| `vancouver_province` | Vancouver Province | en_CA | 自定义解析(高维护) | ➖ 未测试 | ➖ 未测试 | - | - | - |
| `vancouver_sun` | Vancouver Sun | en_CA | 自定义解析(高维护) | ➖ 未测试 | ➖ 未测试 | - | - | - |
| `vic_times` | Victoria Times Colonist | en_CA | 自定义解析(高维护) | ➖ 未测试 | ➖ 未测试 | - | - | - |
| `wash_times` | Washington Times | en_US | 未知 | ➖ 未测试 | ➖ 未测试 | - | - | - |
| `windsor_star` | Windsor Star | en_CA | 自定义解析(高维护) | ➖ 未测试 | ➖ 未测试 | - | - | - |
| `wired` | Wired Magazine, Monthly Edition | en | 自定义解析(高维护) | ➖ 未测试 | ➖ 未测试 | - | - | - |

## 附录 A:无法静态提取 URL 的源(多为动态 parse_index,需人工)

`ainonline`、`al_monitor`、`ald`、`aprospect`、`asahi_shimbun_en`、`atlantic`、`atlantic_com`、`bangkokpost`、`boston.com`、`calgary_herald`、`dawn`、`economist_search`、`edmonton_journal`、`entrepeneur`、`factcheck`、`firstpost`、`globaltimes`、`globe_and_mail`、`google_news`、`grantland`、`hindu`、`hindu_business_line`、`hindustan_times_print`、`ieee_spectrum_mag`、`inc`、`lemonde_dip`、`lwn`、`montreal_gazette`、`nature`、`new_yorker`、`new_yorker_com`、`nymag`、`nytimes`、`nytimes_sub`、`observer_gb`、`ottawa_citizen`、`popscience`、`prospectmaguk_free`、`real_clear`、`reason_magazine`、`regina_leader_post`、`saskatoon_star_phoenix`、`satmagazine`、`scprint`、`slate`、`substack`、`theoldie`、`upi`、`vancouver_province`、`vancouver_sun`、`vic_times`、`wash_times`、`windsor_star`、`wired`

## 附录 B:全部 1097 个源按语言分组

### en(479 个)

`1843`、`DrawAndCook`、`TheMITPressReader`、`abc_au`、`adventuregamers`、`afr`、`ainonline`、`al_jazeera`、`al_monitor`、`albert_mohler`、`ald`、`american_thinker`、`amspec`、`anandtech`、`ancient_egypt`、`android_police`、`ap`、`apod`、`aprospect`、`arcamax`、`ars_technica`、`asahi_shimbun_en`、`asianreviewofbooks`、`atlantic`、`atlantic_com`、`azstarnet`、`ba_herald`、`backyard_boss`、`baltimore_sun`、`bangkokpost`、`bar_and_bench`、`barrons`、`bbc`、`bbc_fast`、`bbc_sport`、`before_we_go`、`bellingcat_en`、`biamag_en`、`big_oven`、`big_picture`、`biggovernment`、`billorielly`、`birmingham_evening_mail`、`bloomberg`、`bloomberg-business-week`、`bookforummagazine`、`boston.com`、`boston_globe_print_edition`、`bq_prime`、`business_insider`、`business_standard`、`business_standard_print`、`business_today`、`cacm`、`calgary_herald`、`car_buzz`、`caravan_magazine`、`catholic_news_agency`、`cato`、`cbc_canada`、`cbn`、`chicago_tribune`、`chr_mon`、`christian_post`、`chronicle_higher_ed`、`cincinnati_enquirer`、`cnetnews`、`cnn`、`coda`、`collider`、`comic_book_archive`、`common_dreams`、`computer_weekly`、`cosmos`、`cracked_com`、`crikey`、`daily_mail`、`daily_mirror`、`daily_writing_tips`、`dailyreckoning`、`dark_horizons`、`dawn`、`debunkingdenialism`、`deccan_herald`、`democracy_journal`、`democracy_now`、`den_of_geek`、`denver_post`、`desiring_god`、`deutsche_welle_en`、`discover_magazine_monthly`、`distrowatch_weekly`、`dna`、`dot_net`、`dual_shockers`、`economia`、`economist`、`economist_espresso`、`economist_free`、`economist_news`、`economist_search`、`economist_world_ahead`、`edmonton_journal`、`en_globes_co_il`、`endgadget`、`entrepeneur`、`epl_talk`、`epoch_times`、`epw`、`epw_magazine`、`equestria_daily`、`espn`、`esquire`、`everett_herald`、`factcheck`、`fairbanks_daily`、`fan_graphs`、`fastcompany`、`financial_times`、`financialsense`、`first_things`、`firstpost`、`flickr`、`football_fancast`、`football_league_world`、`foreign_policy`、`foreignaffairs`、`fortune_magazine`、`foxnews`、`free_inquiry`、`freenature`、`frieze`、`frontline`、`fudzilla`、`futurismic`、`gagadget_en`、`galaxys_edge`、`game_rant`、`gates_notes`、`github`、`give_me_sports`、`gkt`、`glasgow_herald`、`glenn_greenwald`、`globaltimes`、`globe_and_mail`、`go_comics`、`good_ereader`、`good_house_keeping`、`granta`、`grantland`、`greatist`、`greensboro_news_and_record`、`guardian`、`haaretz_en`、`habr`、`hackernews`、`hamilton_spectator`、`hardcore_gamer`、`harpers`、`hbr`、`healthline`、`high_country_news`、`himal_southasian`、`hindu`、`hindu_business_line`、`hindu_post`、`hindufeeds`、`hinduism_today`、`hindustan_times`、`hindustan_times_print`、`history_today`、`horizons`、`hot_cars`、`hotair`、`howtogeek`、`ieee_spectrum_mag`、`ieeespectrum`、`inc`、`inc42`、`independent`、`independent_australia`、`india_facts`、`india_legal_magazine`、`india_speaks_reddit`、`india_today`、`indian_express`、`indic_today`、`inquirer_net`、`instapaper`、`intelligencer`、`interfax`、`iol_za`、`irish_independent`、`irish_times`、`irish_times_free`、`jacobinmag`、`jakarta_post`、`japan_news`、`japan_times`、`javalobby`、`johm`、`journalofaccountancy`、`jpost`、`kirkusreviews`、`korea_herald`、`krebs_on_security`、`kstar`、`kyivpost_en`、`lamebook`、`las_vegas_review`、`latimes`、`le_monde_en`、`lemonde_dip`、`lex_fridman_podcast`、`lifehacker`、`lightspeed_magazine`、`linux_magazine`、`list_apart`、`live_law`、`livemint`、`livescience`、`lrb`、`ludwig_mises`、`lwn`、`lwn_free`、`macrobusiness`、`mail_and_guardian`、`mainichi_en`、`make_use_of`、`martinfowler`、`mdj`、`medscape`、`meduza`、`merco_press`、`miami_herald`、`michellemalkin`、`military_history`、`minerva_magazine`、`mit_technology_review`、`moneycontrol`、`montreal_gazette`、`moscow_times`、`moscowtimes_en`、`motherjones`、`movie_web`、`msnbc`、`mwjournal`、`nasa`、`natgeo`、`natgeo_kids`、`natgeo_traveller`、`natgeohis`、`natgeomag`、`national_post`、`nature`、`nautilus`、`navy_times`、`nejm`、`neowin`、`new_scientist`、`new_scientist_mag`、`new_statesman`、`new_york_review_of_books`、`new_york_review_of_books_no_sub`、`new_yorker`、`new_yorker_com`、`newrepublicmag`、`news24`、`news_busters`、`newslaundry`、`newsminute`、`newsstraitstimes`、`nikkeiasia`、`nme`、`novaya_gazeta_europe_en`、`novinite`、`npr`、`nspm_int`、`nv_en`、`nymag`、`nypost`、`nyt_magazine`、`nyt_tmag`、`nytfeeds`、`nytimes`、`nytimes_cooking`、`nytimes_sports`、`nytimes_sub`、`nytimes_tech`、`nytimesbook`、`nzherald`、`oakland_north`、`observer_gb`、`oc_register`、`oilprice`、`oldnewthing`、`omgubuntu`、`open_magazine`、`opindia`、`orfonline`、`ottawa_citizen`、`ourdailybread`、`outlook_business_magazine`、`outlook_india`、`oxford_mail`、`pajama`、`parisreview`、`pc_advisor`、`pc_world`、`phillosophy_now`、`phoronix`、`phys_org`、`planet_kde`、`planet_python`、`pocket_lint`、`poliitico_eu`、`politico`、`popscience`、`pragyata`、`pravda_ukraine`、`prekshaa`、`press_information_bureau`、`private_eye`、`project_syndicate`、`propublica`、`prospectmaguk_free`、`psych`、`publicdomainreview_org`、`quanta_magazine`、`queueacmorg`、`radio_prague`、`rds`、`readers_digest`、`readersdigest_thehealthy`、`real_clear`、`real_world_economics_review`、`reason_magazine`、`regina_leader_post`、`reuters`、`rte`、`rtnews`、`rushisaband`、`russiafeed`、`salon`、`san_fran_chronicle`、`sanjosemercurynews`、`saskatoon_star_phoenix`、`satmagazine`、`science_advances`、`science_based_medicine`、`science_journal`、`science_news`、`science_x`、`scientific_american`、`sciimmunol`、`scirobotics`、`scisignaling`、`scistm`、`scmp`、`scott_hanselman`、`scprint`、`screen_rant`、`scroll`、`seattle_times`、`seminar_magazine`、`sfbg`、`sign_of_the_times`、`simple_flying`、`skeptic`、`skeptical_enquirer`、`slashdot`、`slate`、`smashing`、`smh`、`smith`、`sonar21`、`spectator-au`、`spectator_magazine`、`spiegel_int`、`sports_illustrated`、`sportstar`、`sputnik`、`st_louis_post_dispatch`、`stackoverflow`、`standardmedia_ke`、`staradvertiser`、`straitstimes`、`strange_horizons`、`stratechery`、`substack`、`swarajya`、`t_invariant_en`、`techcrunch`、`techdirt`、`tehelka`、`teleread`、`the_age`、`the_baffler`、`the_budget_fashionista`、`the_conversation`、`the_diplomat`、`the_ebook_reader`、`the_federalist`、`the_friday_times`、`the_gamer`、`the_journal`、`the_monthly`、`the_nation`、`the_oz`、`the_philippine_daily_inquirer`、`the_register`、`the_richest`、`the_saturday_paper`、`the_sportster`、`the_sun`、`the_things`、`the_travel`、`the_verge`、`the_week`、`the_week_magazine_free`、`the_week_uk`、`the_wire`、`thedailywtf`、`theecocolapse`、`theeconomictimes_india`、`theeconomictimes_india_print_edition`、`theindiaforum`、`thenewcriterion`、`theoldie`、`theonlinephotographer`、`theprint`、`thestar`、`tillsonburg`、`time_magazine`、`times_of_malta`、`times_online`、`tls_mag`、`tmz`、`toi`、`toiprint`、`top_speed`、`truthout`、`tulsaworld`、`ukrinform_en`、`uncrate`、`unian_net_en`、`united_nations`、`universe_today`、`unz`、`upi`、`usatoday`、`usnews`、`vancouver_province`、`vancouver_sun`、`variety`、`vic_times`、`villagevoice`、`vox`、`walrusmag`、`wash_post`、`wash_post_print`、`wash_times`、`windsor_star`、`winnipeg_free_press`、`wired`、`wired_daily`、`words_without_borders`、`world_archeology`、`worldcrunch`、`wsj`、`wsj_free`、`wsj_mag`、`wsj_news`、`xda`、`xkcd`、`yahoo_news`、`zerohedge`、`znetwork`

### ru(85 个)

`3dnews`、`7x7`、`agents`、`aif_ru`、`baikaljournal`、`bbc_ru`、`bellingcat_ru`、`breaking_mad`、`cedar`、`cherta`、`coda_ru`、`colta`、`currenttime`、`deutsche_welle_ru`、`dovod`、`echo_moskvy`、`fontanka`、`gagadget_ru`、`gazetaua_ru`、`geekcity`、`gorky`、`grani`、`habr_ru`、`id_pixel`、`interfax_uk`、`istories`、`ixbt`、`izvestia`、`knife_media`、`kommersant`、`kompiutierra`、`lenta_ru`、`liganet_ru`、`media_zone`、`meduza_ru`、`mel`、`moscowtimes_ru`、`n_kaliningrad`、`n_plus_one`、`navalny`、`newtab`、`newtimes`、`novaya_gazeta`、`novaya_gazeta_europe`、`nv_ru`、`oba`、`old_games`、`opennet`、`osvitaua_ru`、`paperpaper`、`poligon`、`pravda_ru`、`pravda_ukraine_ru`、`project`、`prosleduet`、`rbc_ru`、`rbcua_ru`、`ria_ru`、`rosbalt`、`rt`、`snob`、`sobaka`、`sobesednik`、`sotavision`、`sova`、`stopgame`、`t_invariant_ru`、`takiedela`、`tayga`、`the_insider`、`thebell`、`tjournal`、`trv`、`tst`、`ukrinform_ru`、`unian_net`、`unn_ru`、`vedomosti`、`verstka`、`vikna_ru`、`wicomix`、`wonderzine`、`zadolba_li`、`zerkalo`、`zn_ru`

### de(85 个)

`berfreunde_blog`、`bild_de`、`brigitte_de`、`cachys_blog`、`cicero`、`dachauer_nachrichten`、`der_standard`、`deredactie`、`deutsche_welle_de`、`deutschland_funk`、`diepresse`、`dorfener_anzeiger`、`ebetrsberger_zeitung`、`erdinger_anzeiger`、`faz_net`、`fluter_de`、`focus_de`、`freisinger_tagblatt`、`furstenfeldbrucker_tagblatt`、`gagadget_de`、`garmischer_tagblatt`、`geretsrieder_merkur`、`golem_de`、`gwup`、`handelsblatt`、`hannoversche_zeitung`、`heise`、`heise_ct`、`heise_ix`、`hna`、`holzkirchener_merkur`、`impulse_de`、`isar-loisachbote`、`karlsruhe`、`kath_net`、`kleinezeitung`、`kurier`、`linux_news_de`、`mallorca_zeitung`、`marctv`、`max_planck`、`miesbacher_merkur`、`munchner_merkur_nord`、`munchner_merkur_stadt`、`munchner_merkur_sud`、`munchner_merkur_wurmtal`、`murnauer_tagblatt`、`my_dealz_de`、`nachdenkseiten`、`netzpolitik`、`nzz_ger`、`penzberger_merkur`、`polizeipress_de`、`presse_portal`、`pro_physik`、`rnd`、`saechsische`、`salzburger_nachrichten`、`schongauer_nachrichten`、`scinexx`、`spektrum`、`spiegelde`、`starnberger_merkur`、`sueddeutsche`、`sz_magazin`、`t3n_de`、`t_online`、`tagesan`、`tagespost`、`tagesschau_de`、`tagesspiegel`、`taz_rss`、`tegernseer_zeitung`、`telepolis`、`titanic_de`、`tolzer_kurier`、`ukrinform_de`、`warentest`、`weilheimer_tagblatt`、`welt`、`welt_der_physik`、`woz_die`、`zackzack`、`zeitde`、`zeitde_sub`

### pl(76 个)

`alejakomiksu_com`、`android_com_pl`、`antyweb`、`astro_news_pl`、`benchmark_pl`、`brewiarz`、`di`、`dobreprogamy`、`drytooling_pl`、`dwutygodnik`、`dzieje_pl`、`dziennik_baltycki`、`dziennik_lodzki`、`dziennik_pl`、`dziennik_polski`、`dziennik_wschodni`、`dziennik_zachodni`、`dziennikzwiazkowy`、`elektroda_pl`、`esenja`、`esensja_(rss)`、`eso_pl`、`film_org_pl`、`film_web`、`focus_pl`、`gameplay_pl`、`gazeta-prawna-calibre-v1`、`gazeta_krakowska`、`gazeta_pl_krakow`、`gazeta_pl_warszawa`、`geopolityka`、`gosc_full`、`gosc_niedzielny`、`gram_pl`、`gry_online_pl`、`hatalska`、`historia_pl`、`in4_pl`、`konflikty_zbrojne`、`kopalniawiedzy`、`kosmonauta_pl`、`kresy_pl`、`kurier_lubelski`、`legeartis`、`linuxportal_pl`、`lomza`、`mateusz_czytania`、`media2`、`michalkiewicz`、`myapple_pl`、`najwyzszy_czas`、`naszdziennik`、`natemat_pl`、`newsweek_polska`、`niebezpiecznik`、`nowy_obywatel`、`optyczne_pl`、`osw`、`pc_foster`、`polter_pl`、`ppe_pl`、`pure_pc`、`rmf24_ESKN`、`rmf24_fakty`、`rmf24_opinie`、`rzeczpospolita`、`satkurier`、`sekurak_pl`、`swiat_obrazu`、`swiatkindle`、`tablety_pl`、`ukrinform_pl`、`wirtualnemedia_pl`、`wnp`、`znadplanszy_pl`、`zycie_warszawy`

### es(69 个)

`10minutos`、`20_minutos`、`abc_es`、`ambito`、`animal_politico`、`asco_de_vida`、`attac_es`、`bbc_es`、`bellingcat_es`、`cenital`、`ciperchile`、`clarin`、`cubadebate`、`deutsche_welle_es`、`diario_el_pueblo`、`diario_ibiza`、`diario_sport`、`diariovasco`、`el_colombiano`、`el_confidencial`、`el_correo`、`el_cultural`、`el_diario`、`el_diplo`、`el_economista`、`el_faro`、`el_mercurio_chile`、`el_mundo_today`、`el_pais`、`el_pais_babelia`、`el_pais_uy`、`elcohetealaluna`、`elcronista-arg`、`elmundo`、`elpais_semanal`、`elperiodico_spanish`、`europa_press`、`expansion_spanish`、`flickr_es`、`gagadget_es`、`grandes_corresponsales_es`、`granma`、`hoy`、`infobae`、`iprofesional`、`jot_down`、`juventudrebelde`、`la_jornada`、`la_nacion_cr`、`la_nueva`、`lanacion`、`lapoliticaonline_ar`、`laprensa`、`libertad_digital`、`marca`、`montevideo_com`、`muy_interesante_mexico`、`national_geographic_es`、`padreydecano`、`pagina12`、`pagina_12_print_ed`、`portafolio`、`queleer`、`red_voltaire`、`revista_muy`、`revista_veintitres`、`the_clinic_online`、`ukrinform_es`、`weblogs_sl`

### fr(40 个)

`20minutes`、`acrimed`、`afrique_21`、`alternatives_economiques`、`arret_sur_images`、`bellingcat_fr`、`canardpc`、`contretemps`、`courrierinternational`、`developpez`、`dhnet_be`、`frandroid`、`gagadget_fr`、`gamekult`、`jeuxvideo`、`korben`、`l_humanite`、`la_presse`、`lalibre_be`、`le_canard_enchaine`、`le_equipe`、`le_gorafi`、`le_monde`、`le_monde_diplomatique_fr`、`le_monde_sub`、`le_monde_sub_paper`、`le_nouvel_observateur`、`le_parisien`、`le_peuple_breton`、`ledevoir`、`lepoint`、`lexpress`、`liberation`、`mediapart`、`orient_21`、`radio_canada`、`telerama`、`ukrinform_fr`、`zdnet.fr`、`zerodeux`

### cs(28 个)

`abc`、`aktualne.cz`、`argument`、`blesk`、`ceske_noviny`、`ct24`、`denik.cz`、`denik.to`、`denik_referendum`、`denikn.cz`、`digizone`、`idnes`、`kudy_z_nudy`、`lupa`、`mesec`、`neviditelny_pes`、`novinky`、`novinky.cz`、`parlamentni_listy`、`podnikatel`、`prvnizpravy`、`reflex_cz`、`respekt_magazine`、`root`、`seznamzpravy`、`syrzdarma`、`tyden.cz`、`vitalia`

### ro(23 个)

`adevarul`、`bucataras`、`catavencii`、`catavencu`、`csid`、`descopera`、`dilema`、`financiarul`、`gsp`、`imperatortravel`、`intrefete`、`kudika`、`mediafax`、`monden`、`observatorul_cultural`、`onemagazine`、`revista22`、`sfin`、`stiintasitehnica`、`tabu`、`tvmania`、`ziarulfinanciar`、`ziuaveche`

### it(23 个)

`adnkronos`、`contropiano`、`corriere_della_sera_it`、`disinformatico`、`gagadget_it`、`il_cambiamento`、`il_fatto`、`il_messaggero`、`il_post`、`ilmanifesto`、`ilsole24ore`、`internazionale`、`la_republica`、`la_stampa`、`la_voce`、`lega_nerd`、`leggo_it`、`liberatorio_politico`、`marketing_magazine`、`onda_rock`、`pambianco`、`punto_informatico`、`vignette`

### da(23 个)

`altomdata_dk`、`avisen_dk`、`borsen_dk`、`bt_dk`、`computerworld_dk`、`dagensmedicin_dk`、`dagenspharma_dk`、`dr_dk`、`information_dk`、`ing_dk`、`kommunalsundhed_dk`、`maskinbladet_dk`、`newz_dk`、`nordjyske_dk`、`politiken_dk`、`politiko_dk`、`tv2lorry_dk`、`tv2nord_dk`、`tv2oj_dk`、`tvmidtvest_dk`、`tvsyd_dk`、`ugeskriftet`、`version2`

### uk(22 个)

`bbc_uk`、`bellingcat_uk`、`champion`、`dev_ua`、`footballua`、`gagadget_ua`、`gazetaua_ua`、`interfax_ua`、`kyivpost_ua`、`liganet_ua`、`nv_ua`、`osvitaua`、`pravda_uk`、`radiosvoboda_ua`、`rbcua_ua`、`t_invariant_ua`、`ua_fooball`、`ukrinform_uk`、`unian_net_ua`、`unn_ua`、`vikna_ua`、`zn_ua`

### nl(20 个)

`ad`、`de_standaard`、`degentenaar`、`demorgen_be`、`financieele_dagblad`、`fokkeensukke`、`gagadget_nl`、`gva_be`、`hln_be`、`joop`、`ncrnext`、`nrc-nl-epub`、`nrc.nl`、`nrc_next`、`nu`、`parool`、`tijd`、`trouw`、`tweakers_net`、`volksrant`

### hu(15 个)

`bama`、`blikk`、`ellenpont`、`hvg`、`index_hu`、`kitekinto`、`magyar_nemzet`、`mandiner`、`modoros`、`mult_kor`、`nepszabadsag`、`nol`、`pcworld_hu`、`portfolio`、`telex`

### tr(14 个)

`biamag`、`bianet`、`bugun_gazetesi`、`cumhuriyet`、`derin_dusunce`、`haksoz`、`hurriyet`、`iktibas`、`insan_okur`、`sabah`、`sol_haber`、`star_gazetesi`、`yalansavar`、`yenisafak_gazetesi`

### sr(13 个)

`blic`、`cvecezla`、`danas`、`deutsche_welle_sr`、`nezavisne_novine`、`njuz_net`、`novosti`、`nspm`、`pecat`、`pescanik`、`rts`、`thecultofghoul`、`vreme`

### pt(11 个)

`bbc_brasil`、`cm_journal`、`deutsche_welle_pt`、`economico`、`folha`、`folhadesaopaulo`、`folhadesaopaulo_sub`、`pravda_por`、`publico`、`superinteressante`、`vio_mundo`

### ja(11 个)

`cnetjapan`、`cnetjapan_digital`、`cnetjapan_release`、`jijinews`、`mainichi`、`mainichi_science_news`、`nhk_news`、`nikkei_news`、`ukrinform_ja`、`uninohimitu`、`yomiuri_world`

### zh(7 个)

`am730`、`ifzm`、`liberty_times`、`people_daily`、`singtaohk`、`wenxuecity-znjy`、`zaobao`

### sv(6 个)

`arbetaren`、`dagens_industri`、`dn_se`、`ekot`、`fokus`、`svt_nyheter`

### el(6 个)

`capital_gr`、`in_gr`、`protagon`、`skai`、`the_press_project`、`tovima`

### he(5 个)

`calcalist`、`globes_co_il`、`the_marker`、`walla`、`ynet`

### hr(5 个)

`deutsche_welle_hr`、`dnevnik_cro`、`nacional_cro`、`novilist_novine_hr`、`vecernji_list`

### te(4 个)

`andhrajyothy_ap`、`andhrajyothy_tel`、`eenadu`、`eenadu_ap`

### ko(4 个)

`daum_net`、`donga`、`hankyoreh21`、`kyungyhang`

### ?(3 个)

`google_news`、`nakedcapitalism`、`todoist`

### sl(2 个)

`avto-magazin`、`mmc_rtv`

### hi(2 个)

`dainik_bhaskar`、`hindustan`

### ca(2 个)

`el_nacional`、`news324`

### sc(2 个)

`istorias`、`limba_sarda`

### sk(2 个)

`pravda`、`tyzden`

### ar(1 个)

`al_masry_alyoum_arabic`

### vi(1 个)

`bbcvietnamese`

### bs(1 个)

`deutsche_welle_bs`

### nb(1 个)

`gagadget_nb`

### gl(1 个)

`galicia_confidential`

### en-US(1 个)

`hackaday`

### ta(1 个)

`hindutamil`

### id(1 个)

`mediaindonesia`

### bg(1 个)

`novinite_bg`

### th(1 个)

`thairath`
