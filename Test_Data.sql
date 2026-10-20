-- HERO QA26 測試資料：只新增 qa26_ / [QA26] 專用資料，不刪除或重設既有資料。
-- 使用你原始 SQL 建好的 GamePlatformDB；本檔只有測試資料，不改任何表或欄位。
USE GamePlatformDB;
GO
SET NOCOUNT ON;
SET XACT_ABORT ON;
BEGIN TRY
 BEGIN TRANSACTION;
 DECLARE @lock INT;
 EXEC @lock=sys.sp_getapplock @Resource='HERO_QA26_SEED',@LockMode='Exclusive',@LockOwner='Transaction',@LockTimeout=10000;
 IF @lock<0 THROW 52000,N'測試資料正在建立，請稍後重試',1;
 DECLARE @now DATETIME2=SYSDATETIME();
 DECLARE @FrontendBase VARCHAR(200)='http://localhost:5173';
 -- 如需實際測試寄信，可在首次執行前填一個你持有的信箱，只套用 qa26_mail。
 DECLARE @RealTestEmail VARCHAR(100)=NULL;
 IF OBJECT_ID(N'dbo.admin_user_permissions',N'U') IS NULL OR OBJECT_ID(N'dbo.member_oauth_accounts',N'U') IS NULL
  THROW 52001,N'請先完成原始 SQL 的基礎表與第10段權限初始化',1;
 IF (SELECT COUNT(*) FROM dbo.admin_roles WHERE role_code IN ('ADMIN','STAFF','REVIEWER','SUPPORT','PENDING'))<>5
  THROW 52002,N'請先完成完整 SQL 第10段角色與權限初始化',1;


DECLARE @BuildBytes BIGINT=196;
DECLARE @Attachment VARBINARY(MAX)=0x89504e470d0a1a0a0000000d4948445200000140000000b408020000006a86624600001a6949444154789ceddd775c1447ff07f0ef1e70701cfde8481344908e340b4d11156cb160d7187b2ff189d1180bb126b1c51e4b6cd812b1a04601118d34a352ec8008082820d23938e0eef7c7e23de7354e24c96f9e7cdf2fff386667f716e4733b3b3b3350b603a70042884c8c7ffa041042ed8701468860186084088601468860186084088601468860186084088601468860186084088601468860186084088601468860186084088601468860186084088601468860186084088601468860186084088601468860186084088601468860186084088601468860186084088601468860186084088601468860186084088601468860186084088601468860186084088601468860186084088601468860186084088601468860186084088601468860186084088601468860186084088601468860186084088601468860186084088601468860186084088601468860186084088601468860186084088601468860186084088601468860186084088601468860186084088601468860186084088601468860186084088601fe27991972827ddd5dba5a4bdd6a656aa4ca5451f0502c3555fbcee67696661f75029a6c961147575b83fd517ba1ff3f30c0ed4451d4d6af66ac983e46474ba3dd07e9e9de6defaaf9b3c2c324374d1b31e0dafef5f3c60d51f0508e3616d1bbd69ed9b202006ccc4d02bd5c14d96bce98c1778e6ff96aea28c94d1c1dadecab87b3af1e3633e400005345d9deda5cc193417f1be57ffa044815e4ed3a38d017004686f8ed3a79e9c88518be40b06ec1e4617d7a4aaddf77ead725e5158a1ffffe936c06839a3e62e0e584d4e779856dd66f6a6e010006c5d0d5d288dabe8aa144852fd9f034b740f17714d3c2e7d32f2806a5a3a57168ede2aed69d166eda7b23255db2f2b2a9e1d3460c68f77b893a753561d5ae631d72a87f03bc02b7537c6afaa4e53fe4be7aadc9662d9f3efac4e6657ada9a2a4acaaa4c15a9ffe8bd2c4d0dbb77ebc2d1d16af3f8694f5f5cbc99aca4c4583279b822e7d3dcd202000c25aaa2ba76ebb128352673f7cab92c5566bbbf417ecbfb0003555553979195abca54d9bd72dec8103f59bb701b1adfbcad68f73f6e4363bbcff65febafba02f7f670ecdfcbd3cbc9ce404f5b4559b9ec5de5b39785b1490faefe7197d7d4acc811362f9e3abc5f2f0078f2a260e8fc35726a521415e0e93ca25f6f87ce1646fa3a256f2bb3f20b13d39e9cbe9a20bc8cc8e2666f13dedfbf87ab83819e761db721f7d5eb2bb7ef9efa3da1a5a58d1d012039e3e9a0b9ab974e19f1c567fd3d1c6c3b19ebd3e5d3d76c4fb89b29ac9614b9cd40579b7e3d635468787fff655b0f45c525b679fcdd27a38704f5f07571d0d7d17a5b592dbf72f3fb2b30001cbd18dbc7c7b5a75bb759a3076d3b1625ac63ddc938e6e70d92fb86f7f70fefef2f5ae2356601afa9897e4d5194402088d81bf9aeaa66e184611b167ecee7f3a59eff6fb17722f646b6f97dc912316fd2d8d0c076effeefd4f101ee6c6ef2ddbc49dece5d450b2d4c0c2d4c0c437a7a2c983074ed9e13b7ee3d947f101565e5e09eeef4eb6e3616d666c62f8bde48ad69c4d1fd7ec9d49eeedd842596a68696a686fd7a788c0ce9bdf487032f5ebd96baa3921263f5ec0963060650144597a83255f4b4353d9dec0607f92edeb4bfb8acbccd6fb6a9b979e38133cff30a8d39ba99cf5f8e0f6d738f8f90575cf2dddec8b894b436d30b00bce666005062b436a9d6ed3f7569e71ab12b704b0bffcddb0f9af15a6c7575962ab791575553275a2ee00b1a1b5b03cc5469fd25d975f2523db771f9f4d11b164d297d5779e7c16339e74351545f1f37f9e7cc1708e253d3dbface903c1d1c60377b9b836b17696bb20180dbc8bbf567664e4171537373272383201f577d1d2d7363839fd72e5abdfbf8e9ab09728ed3dbc3518bad5e5651555553676b613ad0df6bcfa968c96a56a64627367f65c4d1150804f1a919d7eedc7b5755d3d9dc7870a0af8b9db593add5ce157386cc5f435f9dc4fcb874faa0001f00282e2b3f1773e7795ea1b1be9e8f4bd7605f770f07db0513867ebdedb082df75546cdb975305495e8556cf99207c3d79c58fcff30a29693b6ab1d50180c1a0f475b400a0a2aa66cc7f361695bcd5d5d2a8a8aea5eb14bc2ef59bf4a5e85ef4bd6b7442ca373b8e481eb381c7536332453f050e9fbfaea6aa12d2b3bbf098b230286aefaaf9f2eb34f29a9c86cd945f07c9d7910136d0d33eb066219ddee88494b57b23453fd79594183346862e9c384c89c188983bf1d5ebb2c434991fe161fede00907037b3aca2cad6c234ccdf5b6a808704f91a71741b78bcc59bf6c7a5a4d185b7ef3f3c1e7d63c3c229c3837b75b1349b3e62e0de3397c5761cdeaf179ddef8d4f4c5dfefafe7b6de7d1dbd181bd2abfbc4c17dd7ee39f1493f8bf6aaa8ae2d2a792b6b6b23af29fed0667596aaac0a1445259fdc2e5a5254f23670ca57ed3b997a6ea31a93a9fae1657ccfe9cbfbce5ce10b048a1c21bfb83478dad752373d8b3ed8beb342a23a32c0ebe64fa69fa99cb9766be54f47c5b6b6b4f0f79eb95c525eb179c9548aa2be5f323578fa72a9fd164c15e5bebe6e0070f36e46e9bbca396306d9599ad95a98e614148bd5fc29f2225f20f8e3fea38ce7b962efb5e1c0e9909e1e1aeaac406f17b100ab3255967d110e002f0bdf2cdcb8af81c713dd1a93783f26f17efb7e02b4036b16b57bdf6dc7a244ef5a3bd0c4c17d174dfa4cac508dc90480cffaf61cd0db5372174d7516001c8e58dcd422a509b333f2e2910bb17fc199a28fd06101ee6ad5a98f8f1b0054d5d66d3a785656b5a8b8c491217e5e4e76861c9de1c1bd222fc74bd609f074d1506735353727a63fe636f0de5656ebeb6885f97bef387141b2f2ae9397a4be51554ddd931705dece5d25c736f4ebe1a1a7ad09005b8e9e134baf82288a9a322ce4f2edd4d2f24ac9ad95d5b50dbc26e197861c1d0625b5d9db1eae23664b2d5763321f5ed807003e6317beabaa91acc05451a69bd992549495559465fe26b0d45459d2ca992a6d0f32b13435ccbe2af34ea459dae702fa281d16e031efefdc8e5c88adade7caa9b9ebe4a5a31b9602c0d8d040a9010ef5f30280d4cce774cb36e16ec6c8103f59019683eeb0d1506769a8b3444f6970a00f0054d5d6dd686f0fcae4a1c1cba78f5e3279f899df6fed3973b9fcc34ea6ff6c3d28ab175a11bd3d1c974c1e21599ef93c778dec867d038fd7d2c257526268a8b3a406f850d4f54351d7e9d7e6c606fe9ece3149f7cbde550140a0b74b767eb164d3fdc8fa2f7bb93b46ec8d3c1e7d43f1f3a7f105822f7ff859f8a5165b7df59c096f2bab371e382d5ae7630f8bc47458807d5dec014020101cbfd4c67f7652fa939c82625b0bd3ae569d44bb58686a4c267d25bf7937832e894fcd1819e267ddc9d8beb3f9b3dc578a9f9211470700b88d3cb10f14377b1b00484c7b22b5734b11c565ef8a4acbcd0c399386060f0ef2fd66c791d8e407ed3b94246d0db673172bc9f29ada7af93bd672b9da1a6c0d75b536df62d290e0cf87f5b3b5305dbbe7c4e80101eb164c8e497a3077dd2eb16af40781eec70c35a3286af4c000c972969a2a0030284a9df5c1e9093ff77fbd7e5bf17741421d13604d36cbc6dc0400de565457d5d6b5599f0e3000b83bd88a3d4808f072a63b698401be93f698d7d4cc54510ef3f7563cc01aea2c3aa8f71e6589961bebebd2ede7a72f3e18a5443fed54f0e03189f76ffd99396558c8bc714374b53476ad9cdb6fdaf282d7a50aee2edf95db77afdcbedb8e1d6beb1bb435d81aea521bbcffc566a9d10fd8e9e6cfa58494a59f8f08e9e9e1ee6093f6f485684d3ac0f48f8bd6c9587fc9a4e1d109a9c2ff1d310c8afa6ede24596fada7ad296bebc5f864f9a78da4ea98001be8e9d04f535f95942952bff04d6b6bcd90a323b689ee7fce29287ef5a6f550dc86c6e48ca7019ecea17e5e5b8e9c53f0944686f4a6c73f5dbe952a5a6e6e6c40bfc82b2e01001f17fb29c342bc9ced545554720b5f3fcd7db5e774747e71db516ce435ed3b7be5466afaae15731e3ccde9a8f4cae7eddc3572f332a99bc62cdd58535b0f861c1dcd366626cc1d3b588bad9e9cf194ee14e436341e3e1fb364f2f08983fb4a0db068fb3fccdf7b70a0af8589a1ac00b7f0f95d42bff8a86f0a7d8a8e09b0cefbe92cc264ca57f0a6f5d75d57f383e6194b4d35d0db150062923e6891c6a5a405783a5b98183ad95a3dcac96bf3f8da1aecb9630603405169f9a58414d14d9aef3b726aeab86be64c183fa88f709343670b87ce1661fede9b0f9d55f0ae2f3bbf68d492f502f8e0d2fd29bdd000d0d3addbfcf14325cb1f65e7c96fa897575503809edc719ad666c69387f603801eae0e62dd4b83037de9d1dd0070f36ec68c353be84e0413438eb0ce207f1f0050641859787f7f8652db037579bc26458e8664e998003318adbdac8ab742a5eae3ed4a0f1b8849fae041ce8d94b488b913298a0af3f75624c06be74da41f686d38705aec465793dddac25c3279b88b9df5f5c4fbbf9c8f79925ba0afa3e5eddcf5eba9e13a5a1adfcc1c7bef71b6823301aaebc46f4d3fb1175a574bc3d3b18b64398fd7b4f1c06997e1b301408bad7ee7f81600a0bf0480c6469e648b578c1a93f9d38ad9f4c8aaccac97fcf7e34cad4c8d74b434b2f28beab90d74496ee11b00282c790b009d8c5a8788d2dd104dcdcd8ab4f057cd1eafc85cc88aea5a0cf0a7e8980057bdef5f118e07964fd88e15fbed0ff5f70680a2d2f2c739f9a2e565efaa329ee7bad9db0cf4f7fafe975fe57f4c8ceaef47b7c37fffe34fc927ba14b466c9b98bd5ca9f8e9eb9768bfef2d59bb2576fcaee3dcefa7ddf3a1565e56f678d1bf7d52645be17499fd80b2dff1e987e72aefcfee226fa20bdbcb206000c65bc979212e387a5d3845302277cfdbd70df6dcb660e0af059b6e590d88723dd2fadaba5410fc60e0ff107809ba91962e32e65c9ca2f0a9bfdad9c0a8f2eec57e438488e8e0970c9fb11b6c24f6bf9ccde577b5df64e58c866a905783a0380992147d6c34333438e6bd7cee9cf5e48dd0a001e0eb66be74e04809c82e2e5db7f91ac20fcc8f829f2a230bd42f9c5a5e762ee8c090d74b7b75152622832a5e1ff0f7abaa289819ed4adab668d1fd0dbb3ec5d95069bc55265ce1c152a9caed0c5d20c0046f5f7f3f774a24b8e47dfa8a9e3be2e7b478fa6b4ef6cfe38277ffca0200088baf171174ca9f7ed7da77efdf7f41afccfeb980057d7d5e7159758991a19eae9a8b35485231365b13633a25f3ccc7a292cecebeba648a32bccdf5b56802d4d0df7ae9aafa2ac5c5d573f3b6267ddfb06a1a8b79555f40bfa11a8a4ccec976320505959c9c2c4f065a1f41914a202bd5cfcba3b7d7ff8d7469166f3a708f2765d3d7bbc64f9c3ecbcf91bf6c8d9916ef1ca0af0d66351861c9dedc7ce1fdff4154b953977ec60b10ae3c28284af2fc627d7d471f90241565e918b9db57d67f31eae0e2c35d5fce2d2843f33e1e3cd5bbfbba8a41c0002bc9c174d141f1086daadc39e0327dccdfc7c583f8aa2c2fbfbcb1f61e76267edd0d902001e66e79555fc3745a17ede0050595dfbc5b75ba5b691b7fc677a677393817e9e1b0e9c966c451be86a1f59b7544f5b932f102cdeb49fee649694f5b2881ef0d0cdc6426a05e12808137d3d45023c7a6040b0af3bafa979f32199e3cf3e8aba9aaa99b486ccebb76dac0750d47acb6a20756b554dddec889dc22f5d86cf166b427fb62042b27f212bafd0c5ceba979ba387832d006c3f71be7dad92acfc22fa87d9d9dcb81dbb23593a2cc007cf5d1b1716c454519e366240e4e59b4dcd3227fdce1addba82cc3e9121ca9a6c965f772700884fcd78989d2775c798e407b3ccc38c38badd1dbb883dddd5d664ffb2fe4bfa0e3c626fe4edfb32a72b36f07899592fdd1d6c7c5ceca53efbb534696d1dbcab96329e498c819e7690972b00c48a749b7f622f34edecf5dbc21942ae5d3bffb66d659bbb14bc2e03004d36cb404f5b56fbe263653ccf1d19e2d7dbc311009ee7155ebdd59e07d40020751e32fa741d16e092f28a73b177c686061a7174a77c16f2f3af57a556f374b20bf67507809c82e2d8e4346179b0af3bdd3b2ae749495c721abd7c5498bfb7688035d45947d67fd9d5aa13006c3b7e5eeaf04c51513712dd1d6c6ccc4d427a7a5c97e8e5a2efc3abebeab3f38be41f07004685f82929315e16be79f0340700f802fe3f75dbacc66436f29a8acbca4d0d385d2ccce800cb199d327fdc90a6f72b2bd03d5be307050987761f8aba4e7716a43e7c2edc65ebd1287af02383a2184a0c05c7b13dcc7a192ad19525daf7813e4547ce46da7ff6ca90205f364b6de9e7231a794dc72ec589fdf6f474efb673c51c7ac8c7b66351a25be9fee7061e4fce1cc3ccac9765efaa0cf4b407f4f65cb7ef24bdda069ba576f8bb254eb6560070ec629cd45987622ec627cf0a0f3333e42c9f36fad9cb57a2c336c2fcbde9b5018e5c886d338a14458d0af10780b331adc300976fffe5d8a51b2d2d2d59efc3afa7add9c5d22c35f319fde5b73f1d5db5f358078e01b6b736efede1e8e7e1e4e9643763cd8ea72f5e991a70bad95a24a53f0180887993a2e2ee880dcfa04d1f3950ac4474ad9cb3d76fd3012e785dca6de4b1549919cf738563e6b435d9fb572f8cd81ba9c8233d6e234f918f42d43e1d19e0a2d2f239eb761d5cbb48455979e5ccb143fbf4381f9798955fd4d4d46c6e6230b0b7571f1f573abd5b8e9c131daaa1adc1eeedee08007fdc7fc46d94393d482010dc484d1f3330807e669b9cf194a5a67a3062b1bb830d00dc7f927de5f6ddde1e8e9a6c75156525b17de352d2845d6bdc86c615db7f3914b1d8cc48ffdcf66f0ffe762dfdd90b8612a3afb7dbc4217d01202bbf48720ab1a4002fe74ec6fa2d2dfc0b714974094b4df5e7350b8df575371f3e7bf0b76b1c1dadeb3faf5757539db4e247bac9c01708e093d36bc4d115761747ef5e2b2cd7d1646766bfecebebe66e6f4b97d85a989ed8b4cc69d84cc9ebb082f7c0cba78da69fcc8bce1cd2d660bb3bd82c983074c69a1d72ced3ced24cce54245a3dae83f5693a78458ea4b427a3bfdcb06dd92c4b5343e72e569283f26bebb9ab761d8ffe707454bf9e1ecaca4a001027d2a896ea464ada9881010010eaef9d9cf1f4db99e384631eba77eb422faa2a55dfa95f1770ff7ba54d4a7f327fc39eefbf9ca6adc1fef2f30fa6fedc7f92bdf4c7038ab40f678c0c0580d8e407c2256fe68f1b62acaf5b54f2f6c4a5780028afacde77f6cab22fc2f7ac9c377c518482c3d4e498342478e290be56a646c2128140f0303b2f36e9416cf28317af5ebbd8592f9ef8998f4b576565a5e6e6164d36abbaaebedda36b46f4eb4d0fdb0280eeddba38da5ad2cfe78df47541e4d9a154eb7f3e255cdf478ec6a68ee9baffd7eaf835b11e66e70d98f5cd9040dfa1413ddcec6de899097c81e0714e7e5c4a5a6474bce46c077afe600b9f1f2f6384ad5052fa937a6ea33a4bb57fafee6bf61cd7d16aff8ae4712969fd67ac983434b88f8f5b2743fd3a6ec3a39cbc5bf71e9eba72539126aebb838d97931d0008e7e839da5a4e19160200ebf69f124e333ef8db35b7ae36fd7b75dfbe6cd6e8a51b14bf431ee4efd3cbad75a12fe1cc5b8aa2e8f4363537a7643cbb919a1e979c26ba5aedc3ecbcf2ca6a8e8e566f0fc784bb99c6fabab23ad2cfef58255cf1cf445f0f00762c9f251c4076333523afb864fdc2cf01e05cec1d4b13434f27bbd5b3278cfd6a634b0bbf8b852900e4ca58a58c764aee9249a8a3fc25ab523637b744c52546c5256aa8b3e20e6ee4e868711b1a176dda27ebd9fd17df6e55f0c8bca666d119ed73be139f01f751ca2aaab61c39a7f80409510b270c0380074f73e887d296a6868722162b2b2bc524de172eee43fb66c711777b1bd7ae9de78d1db2e3c4050645093f208c38ba002035d5ea2c55c9a57312fecce86663119f9afec78347521fb60b048233bfdf9a3376f0a2099fd5731bb535d88f3e1cd32644cf1e13656162287c4d8fbea2282a2e25ed9b1d479cedaccf6c59e1ee60b37ff5c2a31763e90983924fe38707f7eae3ed2af5ed14f1294be4ff6bfdb50bbbd7d673b71d3bbf6ec164364b6df7cab9b32276ca59f089201c1d2dbadb8cbefc1ae8691f5dbf94a3a3f538277fe916f1a59eaa6aeb22f6454e1b31e0da9d7b00f0ebb6952e76d67c818002a07b04a43eb2167d8c246ad9d643f2cfed58f48d8943821d6d2de9f14fb7652c002a7a0f2ce6c7a5d387f6e90100b1c90f166ddad7c2e7a73f7bf1c3e15f974d0d0ff074a67be9f38a4b329ee58aedc866a9b1596dcf46461de82fffcb0cbf5ebfede9d465589f9ef6d6e69776ad397d35e1da9d7b05afcbea1b1a0d74b5959418c269830429afac0e9ebe7ccab090b8a40700505bdf70ebdec3206fd7e96bb64b4dc5f5c4fbb1490fe80b6f4cd27d173b6b7a7a431db7e1f73ffedc7df2839ef3376f2baeddb9f748c6c37045ce6deeba5dbb56ced562abc7a5a4490e9cba76e79eba9a6a8bece56c0e9fbf1ee4e37a223a7efbf1f3c2fbe783e7ae159596cf0a0fb33235caca2ffccf8f07256f348e47dfc075a1ff6694edc0297ff97b50d4ca1963270d0d96dc74f956eae2cdff2323da35d9ac9a3a796b09d158aa4c7d5ded96167e1db74191c50fda474559d940575b91a5ada5d2d3d694ba2e8f2ce6c60626067a25e5158a4ca596c5cad4c890a3535a5e296b141d92f4770498666f6d3e6b7458bf1e1ec285c201e0c5abd703667ef3f79c0042ff7bfebe00d318146562a067696a0400e595d53905c56dfef51384902c7ff75f27e40b0445a5e545a5ed6cda218444e15f27448860186084088601468860186084088601468860186084088601468860186084088601468860186084088601468860186084088601468860186084088601468860186084088601468860186084088601468860186084088601468860186084088601468860186084088601468860186084088601468860186084088601468860186084088601468860186084088601468860186084088601468860186084088601468860186084088601468860186084088601468860186084088601468860186084088601468860186084088601468860186084088601468860186084088601468860186084088601468860186084088601468860186084088601468860186084088601468860186084088601468860186084088601468860186084088601468860186084088601468860186084088601468860186084088601468860186084088601468860186084088601468860186084088601468860186084088601468860186084088601468860186084088601468860ff0794e1f3616acb3a6e0000000049454e44ae426082;
DECLARE @AttachmentBytes INT=6818;
DECLARE @PasswordHash VARCHAR(255)=N'$2b$12$jV5otehsE/oFfvMCiEt4cuhg6SgbhfbC38qguLhqQHxEMYAIpFiG.';

IF EXISTS(SELECT 1 FROM dbo.admin WHERE admin_account=N'qa26_admin' AND (email<>N'qa26_admin@hero.example' OR admin_name NOT LIKE N'QA26%')) THROW 52003,N'測試帳號名稱已被其他資料使用，停止避免誤改',1;

DECLARE @a_admin INT;
SELECT @a_admin=admin_id FROM dbo.admin WITH(UPDLOCK,HOLDLOCK) WHERE admin_account=N'qa26_admin';

IF @a_admin IS NULL BEGIN
 INSERT INTO dbo.admin(admin_account,admin_role_id,admin_name,email,password_hash,status) VALUES(N'qa26_admin',(SELECT admin_role_id FROM dbo.admin_roles WHERE role_code='ADMIN'),N'QA26 王管理',N'qa26_admin@hero.example',@PasswordHash,N'ACTIVE');
 SET @a_admin=CONVERT(INT,SCOPE_IDENTITY());
END;

IF EXISTS(SELECT 1 FROM dbo.admin WHERE admin_account=N'qa26_staff_all' AND (email<>N'qa26_staff_all@hero.example' OR admin_name NOT LIKE N'QA26%')) THROW 52003,N'測試帳號名稱已被其他資料使用，停止避免誤改',1;

DECLARE @InitGrants_staff_all BIT=CASE WHEN EXISTS(SELECT 1 FROM dbo.admin WHERE admin_account=N'qa26_staff_all') THEN 0 ELSE 1 END;

DECLARE @a_staff_all INT;
SELECT @a_staff_all=admin_id FROM dbo.admin WITH(UPDLOCK,HOLDLOCK) WHERE admin_account=N'qa26_staff_all';

IF @a_staff_all IS NULL BEGIN
 INSERT INTO dbo.admin(admin_account,admin_role_id,admin_name,email,password_hash,status) VALUES(N'qa26_staff_all',(SELECT admin_role_id FROM dbo.admin_roles WHERE role_code='STAFF'),N'QA26 林全能',N'qa26_staff_all@hero.example',@PasswordHash,N'ACTIVE');
 SET @a_staff_all=CONVERT(INT,SCOPE_IDENTITY());
END;

IF EXISTS(SELECT 1 FROM dbo.admin WHERE admin_account=N'qa26_staff_limited' AND (email<>N'qa26_staff_limited@hero.example' OR admin_name NOT LIKE N'QA26%')) THROW 52003,N'測試帳號名稱已被其他資料使用，停止避免誤改',1;

DECLARE @InitGrants_staff_limited BIT=CASE WHEN EXISTS(SELECT 1 FROM dbo.admin WHERE admin_account=N'qa26_staff_limited') THEN 0 ELSE 1 END;

DECLARE @a_staff_limited INT;
SELECT @a_staff_limited=admin_id FROM dbo.admin WITH(UPDLOCK,HOLDLOCK) WHERE admin_account=N'qa26_staff_limited';

IF @a_staff_limited IS NULL BEGIN
 INSERT INTO dbo.admin(admin_account,admin_role_id,admin_name,email,password_hash,status) VALUES(N'qa26_staff_limited',(SELECT admin_role_id FROM dbo.admin_roles WHERE role_code='STAFF'),N'QA26 陳限權',N'qa26_staff_limited@hero.example',@PasswordHash,N'ACTIVE');
 SET @a_staff_limited=CONVERT(INT,SCOPE_IDENTITY());
END;

IF EXISTS(SELECT 1 FROM dbo.admin WHERE admin_account=N'qa26_reviewer' AND (email<>N'qa26_reviewer@hero.example' OR admin_name NOT LIKE N'QA26%')) THROW 52003,N'測試帳號名稱已被其他資料使用，停止避免誤改',1;

DECLARE @a_reviewer INT;
SELECT @a_reviewer=admin_id FROM dbo.admin WITH(UPDLOCK,HOLDLOCK) WHERE admin_account=N'qa26_reviewer';

IF @a_reviewer IS NULL BEGIN
 INSERT INTO dbo.admin(admin_account,admin_role_id,admin_name,email,password_hash,status) VALUES(N'qa26_reviewer',(SELECT admin_role_id FROM dbo.admin_roles WHERE role_code='REVIEWER'),N'QA26 張審核',N'qa26_reviewer@hero.example',@PasswordHash,N'ACTIVE');
 SET @a_reviewer=CONVERT(INT,SCOPE_IDENTITY());
END;

IF EXISTS(SELECT 1 FROM dbo.admin WHERE admin_account=N'qa26_support' AND (email<>N'qa26_support@hero.example' OR admin_name NOT LIKE N'QA26%')) THROW 52003,N'測試帳號名稱已被其他資料使用，停止避免誤改',1;

DECLARE @a_support INT;
SELECT @a_support=admin_id FROM dbo.admin WITH(UPDLOCK,HOLDLOCK) WHERE admin_account=N'qa26_support';

IF @a_support IS NULL BEGIN
 INSERT INTO dbo.admin(admin_account,admin_role_id,admin_name,email,password_hash,status) VALUES(N'qa26_support',(SELECT admin_role_id FROM dbo.admin_roles WHERE role_code='SUPPORT'),N'QA26 李客服',N'qa26_support@hero.example',@PasswordHash,N'ACTIVE');
 SET @a_support=CONVERT(INT,SCOPE_IDENTITY());
END;

IF EXISTS(SELECT 1 FROM dbo.admin WHERE admin_account=N'qa26_pending' AND (email<>N'qa26_pending@hero.example' OR admin_name NOT LIKE N'QA26%')) THROW 52003,N'測試帳號名稱已被其他資料使用，停止避免誤改',1;

DECLARE @a_pending INT;
SELECT @a_pending=admin_id FROM dbo.admin WITH(UPDLOCK,HOLDLOCK) WHERE admin_account=N'qa26_pending';

IF @a_pending IS NULL BEGIN
 INSERT INTO dbo.admin(admin_account,admin_role_id,admin_name,email,password_hash,status) VALUES(N'qa26_pending',(SELECT admin_role_id FROM dbo.admin_roles WHERE role_code='PENDING'),N'QA26 周待審',N'qa26_pending@hero.example',@PasswordHash,N'ACTIVE');
 SET @a_pending=CONVERT(INT,SCOPE_IDENTITY());
END;

IF EXISTS(SELECT 1 FROM dbo.admin WHERE admin_account=N'qa26_disabled' AND (email<>N'qa26_disabled@hero.example' OR admin_name NOT LIKE N'QA26%')) THROW 52003,N'測試帳號名稱已被其他資料使用，停止避免誤改',1;

DECLARE @a_disabled INT;
SELECT @a_disabled=admin_id FROM dbo.admin WITH(UPDLOCK,HOLDLOCK) WHERE admin_account=N'qa26_disabled';

IF @a_disabled IS NULL BEGIN
 INSERT INTO dbo.admin(admin_account,admin_role_id,admin_name,email,password_hash,status) VALUES(N'qa26_disabled',(SELECT admin_role_id FROM dbo.admin_roles WHERE role_code='STAFF'),N'QA26 趙停用',N'qa26_disabled@hero.example',@PasswordHash,N'INACTIVE');
 SET @a_disabled=CONVERT(INT,SCOPE_IDENTITY());
END;

IF @InitGrants_staff_all=1 BEGIN

IF NOT EXISTS(SELECT 1 FROM dbo.admin_user_permissions WHERE admin_id=@a_staff_all AND permission_id=(SELECT permission_id FROM dbo.admin_permissions WHERE permission_code='DASHBOARD_VIEW'))
 INSERT INTO dbo.admin_user_permissions(admin_id,permission_id,granted_by_admin_id) VALUES(@a_staff_all,(SELECT permission_id FROM dbo.admin_permissions WHERE permission_code='DASHBOARD_VIEW'),@a_admin);

IF NOT EXISTS(SELECT 1 FROM dbo.admin_user_permissions WHERE admin_id=@a_staff_all AND permission_id=(SELECT permission_id FROM dbo.admin_permissions WHERE permission_code='MEMBER_SUSPEND'))
 INSERT INTO dbo.admin_user_permissions(admin_id,permission_id,granted_by_admin_id) VALUES(@a_staff_all,(SELECT permission_id FROM dbo.admin_permissions WHERE permission_code='MEMBER_SUSPEND'),@a_admin);

IF NOT EXISTS(SELECT 1 FROM dbo.admin_user_permissions WHERE admin_id=@a_staff_all AND permission_id=(SELECT permission_id FROM dbo.admin_permissions WHERE permission_code='PUBLISHER_REVIEW'))
 INSERT INTO dbo.admin_user_permissions(admin_id,permission_id,granted_by_admin_id) VALUES(@a_staff_all,(SELECT permission_id FROM dbo.admin_permissions WHERE permission_code='PUBLISHER_REVIEW'),@a_admin);

IF NOT EXISTS(SELECT 1 FROM dbo.admin_user_permissions WHERE admin_id=@a_staff_all AND permission_id=(SELECT permission_id FROM dbo.admin_permissions WHERE permission_code='ORDER_VIEW'))
 INSERT INTO dbo.admin_user_permissions(admin_id,permission_id,granted_by_admin_id) VALUES(@a_staff_all,(SELECT permission_id FROM dbo.admin_permissions WHERE permission_code='ORDER_VIEW'),@a_admin);

IF NOT EXISTS(SELECT 1 FROM dbo.admin_user_permissions WHERE admin_id=@a_staff_all AND permission_id=(SELECT permission_id FROM dbo.admin_permissions WHERE permission_code='REFUND_MANAGE'))
 INSERT INTO dbo.admin_user_permissions(admin_id,permission_id,granted_by_admin_id) VALUES(@a_staff_all,(SELECT permission_id FROM dbo.admin_permissions WHERE permission_code='REFUND_MANAGE'),@a_admin);

IF NOT EXISTS(SELECT 1 FROM dbo.admin_user_permissions WHERE admin_id=@a_staff_all AND permission_id=(SELECT permission_id FROM dbo.admin_permissions WHERE permission_code='GAME_REVIEW'))
 INSERT INTO dbo.admin_user_permissions(admin_id,permission_id,granted_by_admin_id) VALUES(@a_staff_all,(SELECT permission_id FROM dbo.admin_permissions WHERE permission_code='GAME_REVIEW'),@a_admin);

IF NOT EXISTS(SELECT 1 FROM dbo.admin_user_permissions WHERE admin_id=@a_staff_all AND permission_id=(SELECT permission_id FROM dbo.admin_permissions WHERE permission_code='TAG_MANAGE'))
 INSERT INTO dbo.admin_user_permissions(admin_id,permission_id,granted_by_admin_id) VALUES(@a_staff_all,(SELECT permission_id FROM dbo.admin_permissions WHERE permission_code='TAG_MANAGE'),@a_admin);

IF NOT EXISTS(SELECT 1 FROM dbo.admin_user_permissions WHERE admin_id=@a_staff_all AND permission_id=(SELECT permission_id FROM dbo.admin_permissions WHERE permission_code='TICKET_HANDLE'))
 INSERT INTO dbo.admin_user_permissions(admin_id,permission_id,granted_by_admin_id) VALUES(@a_staff_all,(SELECT permission_id FROM dbo.admin_permissions WHERE permission_code='TICKET_HANDLE'),@a_admin);

IF NOT EXISTS(SELECT 1 FROM dbo.admin_user_permissions WHERE admin_id=@a_staff_all AND permission_id=(SELECT permission_id FROM dbo.admin_permissions WHERE permission_code='COMMENT_REVIEW'))
 INSERT INTO dbo.admin_user_permissions(admin_id,permission_id,granted_by_admin_id) VALUES(@a_staff_all,(SELECT permission_id FROM dbo.admin_permissions WHERE permission_code='COMMENT_REVIEW'),@a_admin);

IF NOT EXISTS(SELECT 1 FROM dbo.admin_user_permissions WHERE admin_id=@a_staff_all AND permission_id=(SELECT permission_id FROM dbo.admin_permissions WHERE permission_code='PROMOTION_MANAGE'))
 INSERT INTO dbo.admin_user_permissions(admin_id,permission_id,granted_by_admin_id) VALUES(@a_staff_all,(SELECT permission_id FROM dbo.admin_permissions WHERE permission_code='PROMOTION_MANAGE'),@a_admin);

IF NOT EXISTS(SELECT 1 FROM dbo.admin_user_permissions WHERE admin_id=@a_staff_all AND permission_id=(SELECT permission_id FROM dbo.admin_permissions WHERE permission_code='COUPON_MANAGE'))
 INSERT INTO dbo.admin_user_permissions(admin_id,permission_id,granted_by_admin_id) VALUES(@a_staff_all,(SELECT permission_id FROM dbo.admin_permissions WHERE permission_code='COUPON_MANAGE'),@a_admin);

IF NOT EXISTS(SELECT 1 FROM dbo.admin_user_permissions WHERE admin_id=@a_staff_all AND permission_id=(SELECT permission_id FROM dbo.admin_permissions WHERE permission_code='OPERATION_LOG_VIEW'))
 INSERT INTO dbo.admin_user_permissions(admin_id,permission_id,granted_by_admin_id) VALUES(@a_staff_all,(SELECT permission_id FROM dbo.admin_permissions WHERE permission_code='OPERATION_LOG_VIEW'),@a_admin);

IF NOT EXISTS(SELECT 1 FROM dbo.admin_user_permissions WHERE admin_id=@a_staff_all AND permission_id=(SELECT permission_id FROM dbo.admin_permissions WHERE permission_code='LOGIN_LOG_VIEW'))
 INSERT INTO dbo.admin_user_permissions(admin_id,permission_id,granted_by_admin_id) VALUES(@a_staff_all,(SELECT permission_id FROM dbo.admin_permissions WHERE permission_code='LOGIN_LOG_VIEW'),@a_admin);

END;

IF @InitGrants_staff_limited=1 BEGIN

IF NOT EXISTS(SELECT 1 FROM dbo.admin_user_permissions WHERE admin_id=@a_staff_limited AND permission_id=(SELECT permission_id FROM dbo.admin_permissions WHERE permission_code='ORDER_VIEW'))
 INSERT INTO dbo.admin_user_permissions(admin_id,permission_id,granted_by_admin_id) VALUES(@a_staff_limited,(SELECT permission_id FROM dbo.admin_permissions WHERE permission_code='ORDER_VIEW'),@a_admin);

IF NOT EXISTS(SELECT 1 FROM dbo.admin_user_permissions WHERE admin_id=@a_staff_limited AND permission_id=(SELECT permission_id FROM dbo.admin_permissions WHERE permission_code='TAG_MANAGE'))
 INSERT INTO dbo.admin_user_permissions(admin_id,permission_id,granted_by_admin_id) VALUES(@a_staff_limited,(SELECT permission_id FROM dbo.admin_permissions WHERE permission_code='TAG_MANAGE'),@a_admin);

END;

IF EXISTS(SELECT 1 FROM dbo.members WHERE username=N'qa26_member' AND email<>N'qa26_member@hero.example') THROW 52004,N'測試會員名稱或信箱衝突，停止避免誤改',1;

DECLARE @m_member INT;
SELECT @m_member=member_id FROM dbo.members WITH(UPDLOCK,HOLDLOCK) WHERE username=N'qa26_member';

IF @m_member IS NULL BEGIN
 INSERT INTO dbo.members(username,email,total_points,balance,is_publisher,created_at) VALUES(N'qa26_member',N'qa26_member@hero.example',1000,0,0,DATEADD(DAY,-50,@now));
 SET @m_member=CONVERT(INT,SCOPE_IDENTITY());
END;

IF NOT EXISTS(SELECT 1 FROM dbo.password_history WHERE member_id=@m_member)
 INSERT INTO dbo.password_history(member_id,password_hash) VALUES(@m_member,@PasswordHash);

IF NOT EXISTS(SELECT 1 FROM dbo.member_verifications WHERE verification_token=N'QA26_VERIFY_member')
 INSERT INTO dbo.member_verifications(verification_token,email,sent_at,expires_at,is_verified,verified_at) VALUES(N'QA26_VERIFY_member',N'qa26_member@hero.example',DATEADD(DAY,-1,@now),DATEADD(DAY,3,@now),1,DATEADD(DAY,-1,@now));

IF EXISTS(SELECT 1 FROM dbo.members WHERE username=N'qa26_alice' AND email<>N'qa26_alice@hero.example') THROW 52004,N'測試會員名稱或信箱衝突，停止避免誤改',1;

DECLARE @m_alice INT;
SELECT @m_alice=member_id FROM dbo.members WITH(UPDLOCK,HOLDLOCK) WHERE username=N'qa26_alice';

IF @m_alice IS NULL BEGIN
 INSERT INTO dbo.members(username,email,total_points,balance,is_publisher,created_at) VALUES(N'qa26_alice',N'qa26_alice@hero.example',300,0,0,DATEADD(DAY,-49,@now));
 SET @m_alice=CONVERT(INT,SCOPE_IDENTITY());
END;

IF NOT EXISTS(SELECT 1 FROM dbo.password_history WHERE member_id=@m_alice)
 INSERT INTO dbo.password_history(member_id,password_hash) VALUES(@m_alice,@PasswordHash);

IF NOT EXISTS(SELECT 1 FROM dbo.member_verifications WHERE verification_token=N'QA26_VERIFY_alice')
 INSERT INTO dbo.member_verifications(verification_token,email,sent_at,expires_at,is_verified,verified_at) VALUES(N'QA26_VERIFY_alice',N'qa26_alice@hero.example',DATEADD(DAY,-1,@now),DATEADD(DAY,3,@now),1,DATEADD(DAY,-1,@now));

IF EXISTS(SELECT 1 FROM dbo.members WHERE username=N'qa26_bob' AND email<>N'qa26_bob@hero.example') THROW 52004,N'測試會員名稱或信箱衝突，停止避免誤改',1;

DECLARE @m_bob INT;
SELECT @m_bob=member_id FROM dbo.members WITH(UPDLOCK,HOLDLOCK) WHERE username=N'qa26_bob';

IF @m_bob IS NULL BEGIN
 INSERT INTO dbo.members(username,email,total_points,balance,is_publisher,created_at) VALUES(N'qa26_bob',N'qa26_bob@hero.example',300,0,0,DATEADD(DAY,-48,@now));
 SET @m_bob=CONVERT(INT,SCOPE_IDENTITY());
END;

IF NOT EXISTS(SELECT 1 FROM dbo.password_history WHERE member_id=@m_bob)
 INSERT INTO dbo.password_history(member_id,password_hash) VALUES(@m_bob,@PasswordHash);

IF NOT EXISTS(SELECT 1 FROM dbo.member_verifications WHERE verification_token=N'QA26_VERIFY_bob')
 INSERT INTO dbo.member_verifications(verification_token,email,sent_at,expires_at,is_verified,verified_at) VALUES(N'QA26_VERIFY_bob',N'qa26_bob@hero.example',DATEADD(DAY,-1,@now),DATEADD(DAY,3,@now),1,DATEADD(DAY,-1,@now));

IF EXISTS(SELECT 1 FROM dbo.members WHERE username=N'qa26_locked' AND email<>N'qa26_locked@hero.example') THROW 52004,N'測試會員名稱或信箱衝突，停止避免誤改',1;

DECLARE @m_locked INT;
SELECT @m_locked=member_id FROM dbo.members WITH(UPDLOCK,HOLDLOCK) WHERE username=N'qa26_locked';

IF @m_locked IS NULL BEGIN
 INSERT INTO dbo.members(username,email,total_points,balance,is_publisher,created_at,locked_until,failed_login_count) VALUES(N'qa26_locked',N'qa26_locked@hero.example',300,0,0,DATEADD(DAY,-47,@now),DATEADD(DAY,7,@now),3);
 SET @m_locked=CONVERT(INT,SCOPE_IDENTITY());
END;

IF NOT EXISTS(SELECT 1 FROM dbo.password_history WHERE member_id=@m_locked)
 INSERT INTO dbo.password_history(member_id,password_hash) VALUES(@m_locked,@PasswordHash);

IF NOT EXISTS(SELECT 1 FROM dbo.member_verifications WHERE verification_token=N'QA26_VERIFY_locked')
 INSERT INTO dbo.member_verifications(verification_token,email,sent_at,expires_at,is_verified,verified_at) VALUES(N'QA26_VERIFY_locked',N'qa26_locked@hero.example',DATEADD(DAY,-1,@now),DATEADD(DAY,3,@now),1,DATEADD(DAY,-1,@now));

IF EXISTS(SELECT 1 FROM dbo.members WHERE username=N'qa26_unverified' AND email<>N'qa26_unverified@hero.example') THROW 52004,N'測試會員名稱或信箱衝突，停止避免誤改',1;

DECLARE @m_unverified INT;
SELECT @m_unverified=member_id FROM dbo.members WITH(UPDLOCK,HOLDLOCK) WHERE username=N'qa26_unverified';

IF @m_unverified IS NULL BEGIN
 INSERT INTO dbo.members(username,email,total_points,balance,is_publisher,created_at) VALUES(N'qa26_unverified',N'qa26_unverified@hero.example',300,0,0,DATEADD(DAY,-46,@now));
 SET @m_unverified=CONVERT(INT,SCOPE_IDENTITY());
END;

IF NOT EXISTS(SELECT 1 FROM dbo.password_history WHERE member_id=@m_unverified)
 INSERT INTO dbo.password_history(member_id,password_hash) VALUES(@m_unverified,@PasswordHash);

IF NOT EXISTS(SELECT 1 FROM dbo.member_verifications WHERE verification_token=N'QA26_VERIFY_unverified')
 INSERT INTO dbo.member_verifications(verification_token,email,sent_at,expires_at,is_verified,verified_at) VALUES(N'QA26_VERIFY_unverified',N'qa26_unverified@hero.example',DATEADD(DAY,-1,@now),DATEADD(DAY,3,@now),0,NULL);

IF EXISTS(SELECT 1 FROM dbo.members WHERE username=N'qa26_empty' AND email<>N'qa26_empty@hero.example') THROW 52004,N'測試會員名稱或信箱衝突，停止避免誤改',1;

DECLARE @m_empty INT;
SELECT @m_empty=member_id FROM dbo.members WITH(UPDLOCK,HOLDLOCK) WHERE username=N'qa26_empty';

IF @m_empty IS NULL BEGIN
 INSERT INTO dbo.members(username,email,total_points,balance,is_publisher,created_at) VALUES(N'qa26_empty',N'qa26_empty@hero.example',300,0,0,DATEADD(DAY,-45,@now));
 SET @m_empty=CONVERT(INT,SCOPE_IDENTITY());
END;

IF NOT EXISTS(SELECT 1 FROM dbo.password_history WHERE member_id=@m_empty)
 INSERT INTO dbo.password_history(member_id,password_hash) VALUES(@m_empty,@PasswordHash);

IF NOT EXISTS(SELECT 1 FROM dbo.member_verifications WHERE verification_token=N'QA26_VERIFY_empty')
 INSERT INTO dbo.member_verifications(verification_token,email,sent_at,expires_at,is_verified,verified_at) VALUES(N'QA26_VERIFY_empty',N'qa26_empty@hero.example',DATEADD(DAY,-1,@now),DATEADD(DAY,3,@now),1,DATEADD(DAY,-1,@now));

IF EXISTS(SELECT 1 FROM dbo.members WHERE username=N'qa26_publisher' AND email<>N'qa26_publisher@hero.example') THROW 52004,N'測試會員名稱或信箱衝突，停止避免誤改',1;

DECLARE @m_publisher INT;
SELECT @m_publisher=member_id FROM dbo.members WITH(UPDLOCK,HOLDLOCK) WHERE username=N'qa26_publisher';

IF @m_publisher IS NULL BEGIN
 INSERT INTO dbo.members(username,email,total_points,balance,is_publisher,created_at,company_name,tax_id,website_url,bank_name,bank_account,publisher_description) VALUES(N'qa26_publisher',N'qa26_publisher@hero.example',300,0,1,DATEADD(DAY,-44,@now),N'[QA26] publisher 工作室',N'12345678',@FrontendBase+'/qa-assets/',N'測試銀行',N'000000000000',N'QA26 測試發行商，非真實商家');
 SET @m_publisher=CONVERT(INT,SCOPE_IDENTITY());
END;

IF NOT EXISTS(SELECT 1 FROM dbo.password_history WHERE member_id=@m_publisher)
 INSERT INTO dbo.password_history(member_id,password_hash) VALUES(@m_publisher,@PasswordHash);

IF NOT EXISTS(SELECT 1 FROM dbo.member_verifications WHERE verification_token=N'QA26_VERIFY_publisher')
 INSERT INTO dbo.member_verifications(verification_token,email,sent_at,expires_at,is_verified,verified_at) VALUES(N'QA26_VERIFY_publisher',N'qa26_publisher@hero.example',DATEADD(DAY,-1,@now),DATEADD(DAY,3,@now),1,DATEADD(DAY,-1,@now));

IF EXISTS(SELECT 1 FROM dbo.members WHERE username=N'qa26_publisher2' AND email<>N'qa26_publisher2@hero.example') THROW 52004,N'測試會員名稱或信箱衝突，停止避免誤改',1;

DECLARE @m_publisher2 INT;
SELECT @m_publisher2=member_id FROM dbo.members WITH(UPDLOCK,HOLDLOCK) WHERE username=N'qa26_publisher2';

IF @m_publisher2 IS NULL BEGIN
 INSERT INTO dbo.members(username,email,total_points,balance,is_publisher,created_at,company_name,tax_id,website_url,bank_name,bank_account,publisher_description) VALUES(N'qa26_publisher2',N'qa26_publisher2@hero.example',300,0,1,DATEADD(DAY,-43,@now),N'[QA26] publisher2 工作室',N'12345678',@FrontendBase+'/qa-assets/',N'測試銀行',N'000000000000',N'QA26 測試發行商，非真實商家');
 SET @m_publisher2=CONVERT(INT,SCOPE_IDENTITY());
END;

IF NOT EXISTS(SELECT 1 FROM dbo.password_history WHERE member_id=@m_publisher2)
 INSERT INTO dbo.password_history(member_id,password_hash) VALUES(@m_publisher2,@PasswordHash);

IF NOT EXISTS(SELECT 1 FROM dbo.member_verifications WHERE verification_token=N'QA26_VERIFY_publisher2')
 INSERT INTO dbo.member_verifications(verification_token,email,sent_at,expires_at,is_verified,verified_at) VALUES(N'QA26_VERIFY_publisher2',N'qa26_publisher2@hero.example',DATEADD(DAY,-1,@now),DATEADD(DAY,3,@now),1,DATEADD(DAY,-1,@now));

IF EXISTS(SELECT 1 FROM dbo.members WHERE username=N'qa26_applicant' AND email<>N'qa26_applicant@hero.example') THROW 52004,N'測試會員名稱或信箱衝突，停止避免誤改',1;

DECLARE @m_applicant INT;
SELECT @m_applicant=member_id FROM dbo.members WITH(UPDLOCK,HOLDLOCK) WHERE username=N'qa26_applicant';

IF @m_applicant IS NULL BEGIN
 INSERT INTO dbo.members(username,email,total_points,balance,is_publisher,created_at,company_name,tax_id,website_url,bank_name,bank_account) VALUES(N'qa26_applicant',N'qa26_applicant@hero.example',300,0,0,DATEADD(DAY,-42,@now),N'[QA26] applicant 工作室',N'12345678',@FrontendBase+'/qa-assets/',N'測試銀行',N'000000000000');
 SET @m_applicant=CONVERT(INT,SCOPE_IDENTITY());
END;

IF NOT EXISTS(SELECT 1 FROM dbo.password_history WHERE member_id=@m_applicant)
 INSERT INTO dbo.password_history(member_id,password_hash) VALUES(@m_applicant,@PasswordHash);

IF NOT EXISTS(SELECT 1 FROM dbo.member_verifications WHERE verification_token=N'QA26_VERIFY_applicant')
 INSERT INTO dbo.member_verifications(verification_token,email,sent_at,expires_at,is_verified,verified_at) VALUES(N'QA26_VERIFY_applicant',N'qa26_applicant@hero.example',DATEADD(DAY,-1,@now),DATEADD(DAY,3,@now),1,DATEADD(DAY,-1,@now));

IF EXISTS(SELECT 1 FROM dbo.members WHERE username=N'qa26_rejected' AND email<>N'qa26_rejected@hero.example') THROW 52004,N'測試會員名稱或信箱衝突，停止避免誤改',1;

DECLARE @m_rejected INT;
SELECT @m_rejected=member_id FROM dbo.members WITH(UPDLOCK,HOLDLOCK) WHERE username=N'qa26_rejected';

IF @m_rejected IS NULL BEGIN
 INSERT INTO dbo.members(username,email,total_points,balance,is_publisher,created_at) VALUES(N'qa26_rejected',N'qa26_rejected@hero.example',300,0,0,DATEADD(DAY,-41,@now));
 SET @m_rejected=CONVERT(INT,SCOPE_IDENTITY());
END;

IF NOT EXISTS(SELECT 1 FROM dbo.password_history WHERE member_id=@m_rejected)
 INSERT INTO dbo.password_history(member_id,password_hash) VALUES(@m_rejected,@PasswordHash);

IF NOT EXISTS(SELECT 1 FROM dbo.member_verifications WHERE verification_token=N'QA26_VERIFY_rejected')
 INSERT INTO dbo.member_verifications(verification_token,email,sent_at,expires_at,is_verified,verified_at) VALUES(N'QA26_VERIFY_rejected',N'qa26_rejected@hero.example',DATEADD(DAY,-1,@now),DATEADD(DAY,3,@now),1,DATEADD(DAY,-1,@now));

IF EXISTS(SELECT 1 FROM dbo.members WHERE username=N'qa26_mail' AND email<>COALESCE(@RealTestEmail,N'qa26_mail@hero.example')) THROW 52004,N'測試會員名稱或信箱衝突，停止避免誤改',1;

DECLARE @m_mail INT;
SELECT @m_mail=member_id FROM dbo.members WITH(UPDLOCK,HOLDLOCK) WHERE username=N'qa26_mail';

IF @m_mail IS NULL BEGIN
 INSERT INTO dbo.members(username,email,total_points,balance,is_publisher,created_at) VALUES(N'qa26_mail',COALESCE(@RealTestEmail,N'qa26_mail@hero.example'),300,0,0,DATEADD(DAY,-40,@now));
 SET @m_mail=CONVERT(INT,SCOPE_IDENTITY());
END;

IF NOT EXISTS(SELECT 1 FROM dbo.password_history WHERE member_id=@m_mail)
 INSERT INTO dbo.password_history(member_id,password_hash) VALUES(@m_mail,@PasswordHash);

IF NOT EXISTS(SELECT 1 FROM dbo.member_verifications WHERE verification_token=N'QA26_VERIFY_mail')
 INSERT INTO dbo.member_verifications(verification_token,email,sent_at,expires_at,is_verified,verified_at) VALUES(N'QA26_VERIFY_mail',COALESCE(@RealTestEmail,N'qa26_mail@hero.example'),DATEADD(DAY,-1,@now),DATEADD(DAY,3,@now),1,DATEADD(DAY,-1,@now));

IF NOT EXISTS(SELECT 1 FROM dbo.password_resets WHERE reset_token=N'QA26_RESET_MAIL_TOKEN')
 INSERT INTO dbo.password_resets(reset_token,member_id,sent_at,expires_at,is_reset) VALUES(N'QA26_RESET_MAIL_TOKEN',@m_mail,@now,DATEADD(DAY,1,@now),0);

IF NOT EXISTS(SELECT 1 FROM dbo.publisher_applications WHERE member_id=@m_publisher AND company_name=N'[QA26] publisher 工作室')
 INSERT INTO dbo.publisher_applications(member_id,company_name,tax_id,document_path,approval_status,admin_id,applied_at,reviewed_at,review_comment) VALUES(@m_publisher,N'[QA26] publisher 工作室',N'12345678',N'',N'APPROVED',@a_reviewer,DATEADD(DAY,-4,@now),DATEADD(DAY,-3,@now),N'QA26 測試審核歷史：APPROVED');

IF NOT EXISTS(SELECT 1 FROM dbo.publisher_applications WHERE member_id=@m_publisher2 AND company_name=N'[QA26] publisher2 工作室')
 INSERT INTO dbo.publisher_applications(member_id,company_name,tax_id,document_path,approval_status,admin_id,applied_at,reviewed_at,review_comment) VALUES(@m_publisher2,N'[QA26] publisher2 工作室',N'12345678',N'',N'APPROVED',@a_reviewer,DATEADD(DAY,-4,@now),DATEADD(DAY,-3,@now),N'QA26 測試審核歷史：APPROVED');

IF NOT EXISTS(SELECT 1 FROM dbo.publisher_applications WHERE member_id=@m_applicant AND company_name=N'[QA26] applicant 工作室')
 INSERT INTO dbo.publisher_applications(member_id,company_name,tax_id,document_path,approval_status,admin_id,applied_at,reviewed_at,review_comment) VALUES(@m_applicant,N'[QA26] applicant 工作室',N'12345678',N'',N'PENDING',NULL,DATEADD(DAY,-4,@now),NULL,NULL);

IF NOT EXISTS(SELECT 1 FROM dbo.publisher_applications WHERE member_id=@m_rejected AND company_name=N'[QA26] rejected 工作室')
 INSERT INTO dbo.publisher_applications(member_id,company_name,tax_id,document_path,approval_status,admin_id,applied_at,reviewed_at,review_comment) VALUES(@m_rejected,N'[QA26] rejected 工作室',N'12345678',N'',N'REJECTED',@a_reviewer,DATEADD(DAY,-4,@now),DATEADD(DAY,-3,@now),N'QA26 測試審核歷史：REJECTED');

DECLARE @tag0 INT;
SELECT @tag0=tag_id FROM dbo.tags WITH(UPDLOCK,HOLDLOCK) WHERE tag_name=N'[QA26] 冒險';

IF @tag0 IS NULL BEGIN
 INSERT INTO dbo.tags(tag_name) VALUES(N'[QA26] 冒險');
 SET @tag0=CONVERT(INT,SCOPE_IDENTITY());
END;

DECLARE @tag1 INT;
SELECT @tag1=tag_id FROM dbo.tags WITH(UPDLOCK,HOLDLOCK) WHERE tag_name=N'[QA26] 多人合作';

IF @tag1 IS NULL BEGIN
 INSERT INTO dbo.tags(tag_name) VALUES(N'[QA26] 多人合作');
 SET @tag1=CONVERT(INT,SCOPE_IDENTITY());
END;

DECLARE @tag2 INT;
SELECT @tag2=tag_id FROM dbo.tags WITH(UPDLOCK,HOLDLOCK) WHERE tag_name=N'[QA26] 策略';

IF @tag2 IS NULL BEGIN
 INSERT INTO dbo.tags(tag_name) VALUES(N'[QA26] 策略');
 SET @tag2=CONVERT(INT,SCOPE_IDENTITY());
END;

DECLARE @tag3 INT;
SELECT @tag3=tag_id FROM dbo.tags WITH(UPDLOCK,HOLDLOCK) WHERE tag_name=N'[QA26] 競速';

IF @tag3 IS NULL BEGIN
 INSERT INTO dbo.tags(tag_name) VALUES(N'[QA26] 競速');
 SET @tag3=CONVERT(INT,SCOPE_IDENTITY());
END;

DECLARE @tag4 INT;
SELECT @tag4=tag_id FROM dbo.tags WITH(UPDLOCK,HOLDLOCK) WHERE tag_name=N'[QA26] 未使用標籤';

IF @tag4 IS NULL BEGIN
 INSERT INTO dbo.tags(tag_name) VALUES(N'[QA26] 未使用標籤');
 SET @tag4=CONVERT(INT,SCOPE_IDENTITY());
END;

DECLARE @g_live INT;
SELECT @g_live=game_id FROM dbo.games WITH(UPDLOCK,HOLDLOCK) WHERE game_name=N'[QA26] 星海遠征';

IF @g_live IS NULL BEGIN
 INSERT INTO dbo.games(game_name,member_id,status,price,description,cover_url,release_date,created_at) VALUES(N'[QA26] 星海遠征',@m_publisher,N'PUBLISHED',690,N'[QA26] 測試用遊戲介紹：用於商城、審核、客服與訂單流程，並非可執行遊戲。',@FrontendBase+'/qa-assets/game-1.png',DATEADD(DAY,-15,@now),DATEADD(DAY,-20,@now));
 SET @g_live=CONVERT(INT,SCOPE_IDENTITY());
END;

IF NOT EXISTS(SELECT 1 FROM dbo.game_tags WHERE game_id=@g_live AND tag_id=@tag0)
 INSERT INTO dbo.game_tags(game_id,tag_id) VALUES(@g_live,@tag0);

IF NOT EXISTS(SELECT 1 FROM dbo.game_media WHERE game_id=@g_live AND media_url=@FrontendBase+'/qa-assets/game-1.png')
 INSERT INTO dbo.game_media(game_id,media_url,media_type,display_order) VALUES(@g_live,@FrontendBase+'/qa-assets/game-1.png',N'IMAGE',1);

IF NOT EXISTS(SELECT 1 FROM dbo.game_builds WHERE game_id=@g_live AND version=N'QA26-1.0.0')
 INSERT INTO dbo.game_builds(game_id,version,file_url,file_size,status) VALUES(@g_live,N'QA26-1.0.0',@FrontendBase+'/qa-assets/qa-demo-build.zip',@BuildBytes,N'ACTIVE');

DECLARE @g_gift INT;
SELECT @g_gift=game_id FROM dbo.games WITH(UPDLOCK,HOLDLOCK) WHERE game_name=N'[QA26] 森之旅人';

IF @g_gift IS NULL BEGIN
 INSERT INTO dbo.games(game_name,member_id,status,price,description,cover_url,release_date,created_at) VALUES(N'[QA26] 森之旅人',@m_publisher2,N'PUBLISHED',390,N'[QA26] 測試用遊戲介紹：用於商城、審核、客服與訂單流程，並非可執行遊戲。',@FrontendBase+'/qa-assets/game-2.png',DATEADD(DAY,-15,@now),DATEADD(DAY,-20,@now));
 SET @g_gift=CONVERT(INT,SCOPE_IDENTITY());
END;

IF NOT EXISTS(SELECT 1 FROM dbo.game_tags WHERE game_id=@g_gift AND tag_id=@tag1)
 INSERT INTO dbo.game_tags(game_id,tag_id) VALUES(@g_gift,@tag1);

IF NOT EXISTS(SELECT 1 FROM dbo.game_media WHERE game_id=@g_gift AND media_url=@FrontendBase+'/qa-assets/game-2.png')
 INSERT INTO dbo.game_media(game_id,media_url,media_type,display_order) VALUES(@g_gift,@FrontendBase+'/qa-assets/game-2.png',N'IMAGE',1);

IF NOT EXISTS(SELECT 1 FROM dbo.game_builds WHERE game_id=@g_gift AND version=N'QA26-1.0.0')
 INSERT INTO dbo.game_builds(game_id,version,file_url,file_size,status) VALUES(@g_gift,N'QA26-1.0.0',@FrontendBase+'/qa-assets/qa-demo-build.zip',@BuildBytes,N'ACTIVE');

DECLARE @g_draft INT;
SELECT @g_draft=game_id FROM dbo.games WITH(UPDLOCK,HOLDLOCK) WHERE game_name=N'[QA26] 草稿測試';

IF @g_draft IS NULL BEGIN
 INSERT INTO dbo.games(game_name,member_id,status,price,description,cover_url,release_date,created_at) VALUES(N'[QA26] 草稿測試',@m_publisher,N'DRAFT',490,N'[QA26] 測試用遊戲介紹：用於商城、審核、客服與訂單流程，並非可執行遊戲。',@FrontendBase+'/qa-assets/game-3.png',DATEADD(DAY,30,@now),DATEADD(DAY,-20,@now));
 SET @g_draft=CONVERT(INT,SCOPE_IDENTITY());
END;

IF NOT EXISTS(SELECT 1 FROM dbo.game_tags WHERE game_id=@g_draft AND tag_id=@tag2)
 INSERT INTO dbo.game_tags(game_id,tag_id) VALUES(@g_draft,@tag2);

IF NOT EXISTS(SELECT 1 FROM dbo.game_media WHERE game_id=@g_draft AND media_url=@FrontendBase+'/qa-assets/game-3.png')
 INSERT INTO dbo.game_media(game_id,media_url,media_type,display_order) VALUES(@g_draft,@FrontendBase+'/qa-assets/game-3.png',N'IMAGE',1);

IF NOT EXISTS(SELECT 1 FROM dbo.game_builds WHERE game_id=@g_draft AND version=N'QA26-1.0.0')
 INSERT INTO dbo.game_builds(game_id,version,file_url,file_size,status) VALUES(@g_draft,N'QA26-1.0.0',@FrontendBase+'/qa-assets/qa-demo-build.zip',@BuildBytes,N'ACTIVE');

DECLARE @g_review INT;
SELECT @g_review=game_id FROM dbo.games WITH(UPDLOCK,HOLDLOCK) WHERE game_name=N'[QA26] 待送審草稿';

IF @g_review IS NULL BEGIN
 INSERT INTO dbo.games(game_name,member_id,status,price,description,cover_url,release_date,created_at) VALUES(N'[QA26] 待送審草稿',@m_publisher,N'DRAFT',890,N'[QA26] 測試用遊戲介紹：用於商城、審核、客服與訂單流程，並非可執行遊戲。',@FrontendBase+'/qa-assets/game-1.png',DATEADD(DAY,30,@now),DATEADD(DAY,-20,@now));
 SET @g_review=CONVERT(INT,SCOPE_IDENTITY());
END;

IF NOT EXISTS(SELECT 1 FROM dbo.game_tags WHERE game_id=@g_review AND tag_id=@tag3)
 INSERT INTO dbo.game_tags(game_id,tag_id) VALUES(@g_review,@tag3);

IF NOT EXISTS(SELECT 1 FROM dbo.game_media WHERE game_id=@g_review AND media_url=@FrontendBase+'/qa-assets/game-1.png')
 INSERT INTO dbo.game_media(game_id,media_url,media_type,display_order) VALUES(@g_review,@FrontendBase+'/qa-assets/game-1.png',N'IMAGE',1);

IF NOT EXISTS(SELECT 1 FROM dbo.game_builds WHERE game_id=@g_review AND version=N'QA26-1.0.0')
 INSERT INTO dbo.game_builds(game_id,version,file_url,file_size,status) VALUES(@g_review,N'QA26-1.0.0',@FrontendBase+'/qa-assets/qa-demo-build.zip',@BuildBytes,N'ACTIVE');

DECLARE @g_review2 INT;
SELECT @g_review2=game_id FROM dbo.games WITH(UPDLOCK,HOLDLOCK) WHERE game_name=N'[QA26] 第二款草稿';

IF @g_review2 IS NULL BEGIN
 INSERT INTO dbo.games(game_name,member_id,status,price,description,cover_url,release_date,created_at) VALUES(N'[QA26] 第二款草稿',@m_publisher2,N'DRAFT',290,N'[QA26] 測試用遊戲介紹：用於商城、審核、客服與訂單流程，並非可執行遊戲。',@FrontendBase+'/qa-assets/game-2.png',DATEADD(DAY,30,@now),DATEADD(DAY,-20,@now));
 SET @g_review2=CONVERT(INT,SCOPE_IDENTITY());
END;

IF NOT EXISTS(SELECT 1 FROM dbo.game_tags WHERE game_id=@g_review2 AND tag_id=@tag0)
 INSERT INTO dbo.game_tags(game_id,tag_id) VALUES(@g_review2,@tag0);

IF NOT EXISTS(SELECT 1 FROM dbo.game_media WHERE game_id=@g_review2 AND media_url=@FrontendBase+'/qa-assets/game-2.png')
 INSERT INTO dbo.game_media(game_id,media_url,media_type,display_order) VALUES(@g_review2,@FrontendBase+'/qa-assets/game-2.png',N'IMAGE',1);

IF NOT EXISTS(SELECT 1 FROM dbo.game_builds WHERE game_id=@g_review2 AND version=N'QA26-1.0.0')
 INSERT INTO dbo.game_builds(game_id,version,file_url,file_size,status) VALUES(@g_review2,N'QA26-1.0.0',@FrontendBase+'/qa-assets/qa-demo-build.zip',@BuildBytes,N'ACTIVE');

DECLARE @g_soon INT;
SELECT @g_soon=game_id FROM dbo.games WITH(UPDLOCK,HOLDLOCK) WHERE game_name=N'[QA26] 即將推出';

IF @g_soon IS NULL BEGIN
 INSERT INTO dbo.games(game_name,member_id,status,price,description,cover_url,release_date,created_at) VALUES(N'[QA26] 即將推出',@m_publisher,N'COMING_SOON',590,N'[QA26] 測試用遊戲介紹：用於商城、審核、客服與訂單流程，並非可執行遊戲。',@FrontendBase+'/qa-assets/game-3.png',DATEADD(DAY,30,@now),DATEADD(DAY,-20,@now));
 SET @g_soon=CONVERT(INT,SCOPE_IDENTITY());
END;

IF NOT EXISTS(SELECT 1 FROM dbo.game_tags WHERE game_id=@g_soon AND tag_id=@tag1)
 INSERT INTO dbo.game_tags(game_id,tag_id) VALUES(@g_soon,@tag1);

IF NOT EXISTS(SELECT 1 FROM dbo.game_media WHERE game_id=@g_soon AND media_url=@FrontendBase+'/qa-assets/game-3.png')
 INSERT INTO dbo.game_media(game_id,media_url,media_type,display_order) VALUES(@g_soon,@FrontendBase+'/qa-assets/game-3.png',N'IMAGE',1);

IF NOT EXISTS(SELECT 1 FROM dbo.game_builds WHERE game_id=@g_soon AND version=N'QA26-1.0.0')
 INSERT INTO dbo.game_builds(game_id,version,file_url,file_size,status) VALUES(@g_soon,N'QA26-1.0.0',@FrontendBase+'/qa-assets/qa-demo-build.zip',@BuildBytes,N'ACTIVE');

DECLARE @g_scheduled INT;
SELECT @g_scheduled=game_id FROM dbo.games WITH(UPDLOCK,HOLDLOCK) WHERE game_name=N'[QA26] 下架流程樣本';

IF @g_scheduled IS NULL BEGIN
 INSERT INTO dbo.games(game_name,member_id,status,price,description,cover_url,release_date,created_at) VALUES(N'[QA26] 下架流程樣本',@m_publisher,N'PUBLISHED',390,N'[QA26] 測試用遊戲介紹：用於商城、審核、客服與訂單流程，並非可執行遊戲。',@FrontendBase+'/qa-assets/game-1.png',DATEADD(DAY,-15,@now),DATEADD(DAY,-20,@now));
 SET @g_scheduled=CONVERT(INT,SCOPE_IDENTITY());
END;

IF NOT EXISTS(SELECT 1 FROM dbo.game_tags WHERE game_id=@g_scheduled AND tag_id=@tag2)
 INSERT INTO dbo.game_tags(game_id,tag_id) VALUES(@g_scheduled,@tag2);

IF NOT EXISTS(SELECT 1 FROM dbo.game_media WHERE game_id=@g_scheduled AND media_url=@FrontendBase+'/qa-assets/game-1.png')
 INSERT INTO dbo.game_media(game_id,media_url,media_type,display_order) VALUES(@g_scheduled,@FrontendBase+'/qa-assets/game-1.png',N'IMAGE',1);

IF NOT EXISTS(SELECT 1 FROM dbo.game_builds WHERE game_id=@g_scheduled AND version=N'QA26-1.0.0')
 INSERT INTO dbo.game_builds(game_id,version,file_url,file_size,status) VALUES(@g_scheduled,N'QA26-1.0.0',@FrontendBase+'/qa-assets/qa-demo-build.zip',@BuildBytes,N'ACTIVE');

DECLARE @g_off INT;
SELECT @g_off=game_id FROM dbo.games WITH(UPDLOCK,HOLDLOCK) WHERE game_name=N'[QA26] 已下架';

IF @g_off IS NULL BEGIN
 INSERT INTO dbo.games(game_name,member_id,status,price,description,cover_url,release_date,created_at) VALUES(N'[QA26] 已下架',@m_publisher2,N'OFF_SHELF',190,N'[QA26] 測試用遊戲介紹：用於商城、審核、客服與訂單流程，並非可執行遊戲。',@FrontendBase+'/qa-assets/game-2.png',DATEADD(DAY,-15,@now),DATEADD(DAY,-20,@now));
 SET @g_off=CONVERT(INT,SCOPE_IDENTITY());
END;

IF NOT EXISTS(SELECT 1 FROM dbo.game_tags WHERE game_id=@g_off AND tag_id=@tag3)
 INSERT INTO dbo.game_tags(game_id,tag_id) VALUES(@g_off,@tag3);

IF NOT EXISTS(SELECT 1 FROM dbo.game_media WHERE game_id=@g_off AND media_url=@FrontendBase+'/qa-assets/game-2.png')
 INSERT INTO dbo.game_media(game_id,media_url,media_type,display_order) VALUES(@g_off,@FrontendBase+'/qa-assets/game-2.png',N'IMAGE',1);

IF NOT EXISTS(SELECT 1 FROM dbo.game_builds WHERE game_id=@g_off AND version=N'QA26-1.0.0')
 INSERT INTO dbo.game_builds(game_id,version,file_url,file_size,status) VALUES(@g_off,N'QA26-1.0.0',@FrontendBase+'/qa-assets/qa-demo-build.zip',@BuildBytes,N'ACTIVE');

DECLARE @g_free INT;
SELECT @g_free=game_id FROM dbo.games WITH(UPDLOCK,HOLDLOCK) WHERE game_name=N'[QA26] 免費體驗';

IF @g_free IS NULL BEGIN
 INSERT INTO dbo.games(game_name,member_id,status,price,description,cover_url,release_date,created_at) VALUES(N'[QA26] 免費體驗',@m_publisher2,N'PUBLISHED',0,N'[QA26] 測試用遊戲介紹：用於商城、審核、客服與訂單流程，並非可執行遊戲。',@FrontendBase+'/qa-assets/game-3.png',DATEADD(DAY,-15,@now),DATEADD(DAY,-20,@now));
 SET @g_free=CONVERT(INT,SCOPE_IDENTITY());
END;

IF NOT EXISTS(SELECT 1 FROM dbo.game_tags WHERE game_id=@g_free AND tag_id=@tag0)
 INSERT INTO dbo.game_tags(game_id,tag_id) VALUES(@g_free,@tag0);

IF NOT EXISTS(SELECT 1 FROM dbo.game_media WHERE game_id=@g_free AND media_url=@FrontendBase+'/qa-assets/game-3.png')
 INSERT INTO dbo.game_media(game_id,media_url,media_type,display_order) VALUES(@g_free,@FrontendBase+'/qa-assets/game-3.png',N'IMAGE',1);

IF NOT EXISTS(SELECT 1 FROM dbo.game_builds WHERE game_id=@g_free AND version=N'QA26-1.0.0')
 INSERT INTO dbo.game_builds(game_id,version,file_url,file_size,status) VALUES(@g_free,N'QA26-1.0.0',@FrontendBase+'/qa-assets/qa-demo-build.zip',@BuildBytes,N'ACTIVE');

IF NOT EXISTS(SELECT 1 FROM dbo.game_audit_logs WHERE game_id=@g_live AND comment=N'[QA26] APPROVED 歷史')
 INSERT INTO dbo.game_audit_logs(game_id,comment,admin_id,review_type,decision,reviewed_at) VALUES(@g_live,N'[QA26] APPROVED 歷史',@a_reviewer,N'PUBLISH',N'APPROVED',DATEADD(DAY,-10,@now));

IF NOT EXISTS(SELECT 1 FROM dbo.game_audit_logs WHERE game_id=@g_off AND comment=N'[QA26] REJECTED 歷史')
 INSERT INTO dbo.game_audit_logs(game_id,comment,admin_id,review_type,decision,reviewed_at) VALUES(@g_off,N'[QA26] REJECTED 歷史',@a_reviewer,N'PUBLISH',N'REJECTED',DATEADD(DAY,-10,@now));

DECLARE @c_rate INT;
SELECT @c_rate=coupon_id FROM dbo.coupons WITH(UPDLOCK,HOLDLOCK) WHERE coupon_name=N'[QA26] rate';

IF @c_rate IS NULL BEGIN
 INSERT INTO dbo.coupons(coupon_name,discount_type,discount_value,min_spend,total_limit,per_member_limit,used_count,status,start_at,expires_at) VALUES(N'[QA26] rate',N'RATE',0.8,300,100,5,2,N'ACTIVE',DATEADD(DAY,-10,@now),DATEADD(DAY,30,@now));
 SET @c_rate=CONVERT(INT,SCOPE_IDENTITY());
END;

DECLARE @c_amount INT;
SELECT @c_amount=coupon_id FROM dbo.coupons WITH(UPDLOCK,HOLDLOCK) WHERE coupon_name=N'[QA26] amount';

IF @c_amount IS NULL BEGIN
 INSERT INTO dbo.coupons(coupon_name,discount_type,discount_value,min_spend,total_limit,per_member_limit,used_count,status,start_at,expires_at) VALUES(N'[QA26] amount',N'AMOUNT',100,1000,50,5,0,N'ACTIVE',DATEADD(DAY,-10,@now),DATEADD(DAY,30,@now));
 SET @c_amount=CONVERT(INT,SCOPE_IDENTITY());
END;

DECLARE @c_off INT;
SELECT @c_off=coupon_id FROM dbo.coupons WITH(UPDLOCK,HOLDLOCK) WHERE coupon_name=N'[QA26] off';

IF @c_off IS NULL BEGIN
 INSERT INTO dbo.coupons(coupon_name,discount_type,discount_value,min_spend,total_limit,per_member_limit,used_count,status,start_at,expires_at) VALUES(N'[QA26] off',N'RATE',0.9,0,100,5,0,N'INACTIVE',DATEADD(DAY,-10,@now),DATEADD(DAY,30,@now));
 SET @c_off=CONVERT(INT,SCOPE_IDENTITY());
END;

DECLARE @c_expired INT;
SELECT @c_expired=coupon_id FROM dbo.coupons WITH(UPDLOCK,HOLDLOCK) WHERE coupon_name=N'[QA26] expired';

IF @c_expired IS NULL BEGIN
 INSERT INTO dbo.coupons(coupon_name,discount_type,discount_value,min_spend,total_limit,per_member_limit,used_count,status,start_at,expires_at) VALUES(N'[QA26] expired',N'RATE',0.9,0,100,5,0,N'EXPIRED',DATEADD(DAY,-30,@now),DATEADD(DAY,-1,@now));
 SET @c_expired=CONVERT(INT,SCOPE_IDENTITY());
END;

DECLARE @c_empty INT;
SELECT @c_empty=coupon_id FROM dbo.coupons WITH(UPDLOCK,HOLDLOCK) WHERE coupon_name=N'[QA26] empty';

IF @c_empty IS NULL BEGIN
 INSERT INTO dbo.coupons(coupon_name,discount_type,discount_value,min_spend,total_limit,per_member_limit,used_count,status,start_at,expires_at) VALUES(N'[QA26] empty',N'RATE',0.9,0,1,5,1,N'ACTIVE',DATEADD(DAY,-10,@now),DATEADD(DAY,30,@now));
 SET @c_empty=CONVERT(INT,SCOPE_IDENTITY());
END;

DECLARE @o_paid INT;
SELECT @o_paid=order_id FROM dbo.orders WITH(UPDLOCK,HOLDLOCK) WHERE order_no=N'QA26-ORDER-paid';

IF @o_paid IS NULL BEGIN
 INSERT INTO dbo.orders(order_no,member_id,subtotal_amount,discount_amount,total_amount,order_status,payment_type,points_earned,created_at,paid_at,cancelled_at) VALUES(N'QA26-ORDER-paid',@m_member,690,138,552,N'PAID',N'Credit',55,DATEADD(DAY,-3,@now),DATEADD(DAY,-3,@now),NULL);
 SET @o_paid=CONVERT(INT,SCOPE_IDENTITY());
END;

DECLARE @i_paid INT;
SELECT @i_paid=order_item_id FROM dbo.order_items WITH(UPDLOCK,HOLDLOCK) WHERE order_id=@o_paid AND game_id=@g_live;

IF @i_paid IS NULL BEGIN
 INSERT INTO dbo.order_items(order_id,game_id,quantity,unit_price,discount_amount,is_gift,recipient_member_id,coupon_id) VALUES(@o_paid,@g_live,1,690,138,0,NULL,@c_rate);
 SET @i_paid=CONVERT(INT,SCOPE_IDENTITY());
END;

IF NOT EXISTS(SELECT 1 FROM dbo.member_games WHERE member_id=@m_member AND game_id=@g_live)
 INSERT INTO dbo.member_games(member_id,game_id,order_item_id,obtained_source,is_active,revoked_at,total_play_minutes) VALUES(@m_member,@g_live,@i_paid,N'PURCHASE',1,NULL,60);

DECLARE @o_gift INT;
SELECT @o_gift=order_id FROM dbo.orders WITH(UPDLOCK,HOLDLOCK) WHERE order_no=N'QA26-ORDER-gift';

IF @o_gift IS NULL BEGIN
 INSERT INTO dbo.orders(order_no,member_id,subtotal_amount,discount_amount,total_amount,order_status,payment_type,points_earned,created_at,paid_at,cancelled_at) VALUES(N'QA26-ORDER-gift',@m_member,390,78,312,N'PAID',N'Credit',31,DATEADD(DAY,-2,@now),DATEADD(DAY,-2,@now),NULL);
 SET @o_gift=CONVERT(INT,SCOPE_IDENTITY());
END;

DECLARE @i_gift INT;
SELECT @i_gift=order_item_id FROM dbo.order_items WITH(UPDLOCK,HOLDLOCK) WHERE order_id=@o_gift AND game_id=@g_gift;

IF @i_gift IS NULL BEGIN
 INSERT INTO dbo.order_items(order_id,game_id,quantity,unit_price,discount_amount,is_gift,recipient_member_id,coupon_id) VALUES(@o_gift,@g_gift,1,390,78,1,@m_bob,@c_rate);
 SET @i_gift=CONVERT(INT,SCOPE_IDENTITY());
END;

IF NOT EXISTS(SELECT 1 FROM dbo.member_games WHERE member_id=@m_bob AND game_id=@g_gift)
 INSERT INTO dbo.member_games(member_id,game_id,order_item_id,obtained_source,is_active,revoked_at,total_play_minutes) VALUES(@m_bob,@g_gift,@i_gift,N'GIFT',1,NULL,60);

DECLARE @o_pending INT;
SELECT @o_pending=order_id FROM dbo.orders WITH(UPDLOCK,HOLDLOCK) WHERE order_no=N'QA26-ORDER-pending';

IF @o_pending IS NULL BEGIN
 INSERT INTO dbo.orders(order_no,member_id,subtotal_amount,discount_amount,total_amount,order_status,payment_type,points_earned,created_at,paid_at,cancelled_at) VALUES(N'QA26-ORDER-pending',@m_member,390,0,390,N'PENDING',N'ATM',0,DATEADD(DAY,0,@now),NULL,NULL);
 SET @o_pending=CONVERT(INT,SCOPE_IDENTITY());
END;

DECLARE @i_pending INT;
SELECT @i_pending=order_item_id FROM dbo.order_items WITH(UPDLOCK,HOLDLOCK) WHERE order_id=@o_pending AND game_id=@g_gift;

IF @i_pending IS NULL BEGIN
 INSERT INTO dbo.order_items(order_id,game_id,quantity,unit_price,discount_amount,is_gift,recipient_member_id,coupon_id) VALUES(@o_pending,@g_gift,1,390,0,0,NULL,NULL);
 SET @i_pending=CONVERT(INT,SCOPE_IDENTITY());
END;

DECLARE @o_refund INT;
SELECT @o_refund=order_id FROM dbo.orders WITH(UPDLOCK,HOLDLOCK) WHERE order_no=N'QA26-ORDER-refund';

IF @o_refund IS NULL BEGIN
 INSERT INTO dbo.orders(order_no,member_id,subtotal_amount,discount_amount,total_amount,order_status,payment_type,points_earned,created_at,paid_at,cancelled_at) VALUES(N'QA26-ORDER-refund',@m_member,390,0,390,N'REFUNDED',N'Credit',0,DATEADD(DAY,-4,@now),DATEADD(DAY,-4,@now),NULL);
 SET @o_refund=CONVERT(INT,SCOPE_IDENTITY());
END;

DECLARE @i_refund INT;
SELECT @i_refund=order_item_id FROM dbo.order_items WITH(UPDLOCK,HOLDLOCK) WHERE order_id=@o_refund AND game_id=@g_scheduled;

IF @i_refund IS NULL BEGIN
 INSERT INTO dbo.order_items(order_id,game_id,quantity,unit_price,discount_amount,is_gift,recipient_member_id,coupon_id) VALUES(@o_refund,@g_scheduled,1,390,0,0,NULL,NULL);
 SET @i_refund=CONVERT(INT,SCOPE_IDENTITY());
END;

IF NOT EXISTS(SELECT 1 FROM dbo.member_games WHERE member_id=@m_member AND game_id=@g_scheduled)
 INSERT INTO dbo.member_games(member_id,game_id,order_item_id,obtained_source,is_active,revoked_at,total_play_minutes) VALUES(@m_member,@g_scheduled,@i_refund,N'PURCHASE',0,DATEADD(DAY,-1,@now),0);

DECLARE @o_cancel INT;
SELECT @o_cancel=order_id FROM dbo.orders WITH(UPDLOCK,HOLDLOCK) WHERE order_no=N'QA26-ORDER-cancel';

IF @o_cancel IS NULL BEGIN
 INSERT INTO dbo.orders(order_no,member_id,subtotal_amount,discount_amount,total_amount,order_status,payment_type,points_earned,created_at,paid_at,cancelled_at) VALUES(N'QA26-ORDER-cancel',@m_member,190,0,190,N'CANCELLED',N'Credit',0,DATEADD(DAY,-4,@now),NULL,DATEADD(DAY,-4,@now));
 SET @o_cancel=CONVERT(INT,SCOPE_IDENTITY());
END;

DECLARE @i_cancel INT;
SELECT @i_cancel=order_item_id FROM dbo.order_items WITH(UPDLOCK,HOLDLOCK) WHERE order_id=@o_cancel AND game_id=@g_off;

IF @i_cancel IS NULL BEGIN
 INSERT INTO dbo.order_items(order_id,game_id,quantity,unit_price,discount_amount,is_gift,recipient_member_id,coupon_id) VALUES(@o_cancel,@g_off,1,190,0,0,NULL,NULL);
 SET @i_cancel=CONVERT(INT,SCOPE_IDENTITY());
END;

DECLARE @o_fail INT;
SELECT @o_fail=order_id FROM dbo.orders WITH(UPDLOCK,HOLDLOCK) WHERE order_no=N'QA26-ORDER-fail';

IF @o_fail IS NULL BEGIN
 INSERT INTO dbo.orders(order_no,member_id,subtotal_amount,discount_amount,total_amount,order_status,payment_type,points_earned,created_at,paid_at,cancelled_at) VALUES(N'QA26-ORDER-fail',@m_member,690,0,690,N'CANCELLED',N'Credit',0,DATEADD(DAY,-4,@now),NULL,DATEADD(DAY,-4,@now));
 SET @o_fail=CONVERT(INT,SCOPE_IDENTITY());
END;

DECLARE @i_fail INT;
SELECT @i_fail=order_item_id FROM dbo.order_items WITH(UPDLOCK,HOLDLOCK) WHERE order_id=@o_fail AND game_id=@g_live;

IF @i_fail IS NULL BEGIN
 INSERT INTO dbo.order_items(order_id,game_id,quantity,unit_price,discount_amount,is_gift,recipient_member_id,coupon_id) VALUES(@o_fail,@g_live,1,690,0,0,NULL,NULL);
 SET @i_fail=CONVERT(INT,SCOPE_IDENTITY());
END;

DECLARE @o_expire INT;
SELECT @o_expire=order_id FROM dbo.orders WITH(UPDLOCK,HOLDLOCK) WHERE order_no=N'QA26-ORDER-expire';

IF @o_expire IS NULL BEGIN
 INSERT INTO dbo.orders(order_no,member_id,subtotal_amount,discount_amount,total_amount,order_status,payment_type,points_earned,created_at,paid_at,cancelled_at) VALUES(N'QA26-ORDER-expire',@m_member,390,0,390,N'CANCELLED',N'Credit',0,DATEADD(DAY,-4,@now),NULL,DATEADD(DAY,-4,@now));
 SET @o_expire=CONVERT(INT,SCOPE_IDENTITY());
END;

DECLARE @i_expire INT;
SELECT @i_expire=order_item_id FROM dbo.order_items WITH(UPDLOCK,HOLDLOCK) WHERE order_id=@o_expire AND game_id=@g_gift;

IF @i_expire IS NULL BEGIN
 INSERT INTO dbo.order_items(order_id,game_id,quantity,unit_price,discount_amount,is_gift,recipient_member_id,coupon_id) VALUES(@o_expire,@g_gift,1,390,0,0,NULL,NULL);
 SET @i_expire=CONVERT(INT,SCOPE_IDENTITY());
END;

DECLARE @o_usedcap INT;
SELECT @o_usedcap=order_id FROM dbo.orders WITH(UPDLOCK,HOLDLOCK) WHERE order_no=N'QA26-ORDER-usedcap';

IF @o_usedcap IS NULL BEGIN
 INSERT INTO dbo.orders(order_no,member_id,subtotal_amount,discount_amount,total_amount,order_status,payment_type,points_earned,created_at,paid_at,cancelled_at) VALUES(N'QA26-ORDER-usedcap',@m_alice,690,69,621,N'PAID',N'Credit',62,DATEADD(DAY,-5,@now),DATEADD(DAY,-5,@now),NULL);
 SET @o_usedcap=CONVERT(INT,SCOPE_IDENTITY());
END;

DECLARE @i_usedcap INT;
SELECT @i_usedcap=order_item_id FROM dbo.order_items WITH(UPDLOCK,HOLDLOCK) WHERE order_id=@o_usedcap AND game_id=@g_live;

IF @i_usedcap IS NULL BEGIN
 INSERT INTO dbo.order_items(order_id,game_id,quantity,unit_price,discount_amount,is_gift,recipient_member_id,coupon_id) VALUES(@o_usedcap,@g_live,1,690,69,0,NULL,@c_empty);
 SET @i_usedcap=CONVERT(INT,SCOPE_IDENTITY());
END;

IF NOT EXISTS(SELECT 1 FROM dbo.member_games WHERE member_id=@m_alice AND game_id=@g_live)
 INSERT INTO dbo.member_games(member_id,game_id,order_item_id,obtained_source,is_active,revoked_at,total_play_minutes) VALUES(@m_alice,@g_live,@i_usedcap,N'PURCHASE',1,NULL,60);

DECLARE @trend1 INT;
SELECT @trend1=order_id FROM dbo.orders WITH(UPDLOCK,HOLDLOCK) WHERE order_no=N'QA26-TREND-1';

IF @trend1 IS NULL BEGIN
 INSERT INTO dbo.orders(order_no,member_id,subtotal_amount,discount_amount,total_amount,order_status,payment_type,points_earned,created_at,paid_at) VALUES(N'QA26-TREND-1',@m_alice,390,0,390,N'PAID',N'Credit',39,DATEADD(DAY,-30,@now),DATEADD(DAY,-30,@now));
 SET @trend1=CONVERT(INT,SCOPE_IDENTITY());
END;

DECLARE @trend2 INT;
SELECT @trend2=order_id FROM dbo.orders WITH(UPDLOCK,HOLDLOCK) WHERE order_no=N'QA26-TREND-2';

IF @trend2 IS NULL BEGIN
 INSERT INTO dbo.orders(order_no,member_id,subtotal_amount,discount_amount,total_amount,order_status,payment_type,points_earned,created_at,paid_at) VALUES(N'QA26-TREND-2',@m_alice,390,0,390,N'PAID',N'Credit',39,DATEADD(DAY,-60,@now),DATEADD(DAY,-60,@now));
 SET @trend2=CONVERT(INT,SCOPE_IDENTITY());
END;

DECLARE @trend3 INT;
SELECT @trend3=order_id FROM dbo.orders WITH(UPDLOCK,HOLDLOCK) WHERE order_no=N'QA26-TREND-3';

IF @trend3 IS NULL BEGIN
 INSERT INTO dbo.orders(order_no,member_id,subtotal_amount,discount_amount,total_amount,order_status,payment_type,points_earned,created_at,paid_at) VALUES(N'QA26-TREND-3',@m_alice,390,0,390,N'PAID',N'Credit',39,DATEADD(DAY,-90,@now),DATEADD(DAY,-90,@now));
 SET @trend3=CONVERT(INT,SCOPE_IDENTITY());
END;

DECLARE @trend4 INT;
SELECT @trend4=order_id FROM dbo.orders WITH(UPDLOCK,HOLDLOCK) WHERE order_no=N'QA26-TREND-4';

IF @trend4 IS NULL BEGIN
 INSERT INTO dbo.orders(order_no,member_id,subtotal_amount,discount_amount,total_amount,order_status,payment_type,points_earned,created_at,paid_at) VALUES(N'QA26-TREND-4',@m_alice,390,0,390,N'PAID',N'Credit',39,DATEADD(DAY,-120,@now),DATEADD(DAY,-120,@now));
 SET @trend4=CONVERT(INT,SCOPE_IDENTITY());
END;

DECLARE @trend5 INT;
SELECT @trend5=order_id FROM dbo.orders WITH(UPDLOCK,HOLDLOCK) WHERE order_no=N'QA26-TREND-5';

IF @trend5 IS NULL BEGIN
 INSERT INTO dbo.orders(order_no,member_id,subtotal_amount,discount_amount,total_amount,order_status,payment_type,points_earned,created_at,paid_at) VALUES(N'QA26-TREND-5',@m_alice,390,0,390,N'PAID',N'Credit',39,DATEADD(DAY,-150,@now),DATEADD(DAY,-150,@now));
 SET @trend5=CONVERT(INT,SCOPE_IDENTITY());
END;

DECLARE @trend6 INT;
SELECT @trend6=order_id FROM dbo.orders WITH(UPDLOCK,HOLDLOCK) WHERE order_no=N'QA26-TREND-6';

IF @trend6 IS NULL BEGIN
 INSERT INTO dbo.orders(order_no,member_id,subtotal_amount,discount_amount,total_amount,order_status,payment_type,points_earned,created_at,paid_at) VALUES(N'QA26-TREND-6',@m_alice,390,0,390,N'PAID',N'Credit',39,DATEADD(DAY,-180,@now),DATEADD(DAY,-180,@now));
 SET @trend6=CONVERT(INT,SCOPE_IDENTITY());
END;

IF NOT EXISTS(SELECT 1 FROM dbo.cart_items WHERE member_id=@m_member AND game_id=@g_gift AND recipient_member_id IS NULL)
 INSERT INTO dbo.cart_items(member_id,game_id,recipient_member_id,is_gift) VALUES(@m_member,@g_gift,NULL,0);

IF NOT EXISTS(SELECT 1 FROM dbo.cart_items WHERE member_id=@m_alice AND game_id=@g_gift AND recipient_member_id=@m_empty)
 INSERT INTO dbo.cart_items(member_id,game_id,recipient_member_id,is_gift) VALUES(@m_alice,@g_gift,@m_empty,1);

DECLARE @r_normal INT;
SELECT @r_normal=review_id FROM dbo.game_reviews WITH(UPDLOCK,HOLDLOCK) WHERE member_id=@m_member AND game_id=@g_live;

IF @r_normal IS NULL BEGIN
 INSERT INTO dbo.game_reviews(member_id,game_id,rating,is_recommended,content,status) VALUES(@m_member,@g_live,5,1,N'[QA26] 合作與探索體驗很好，值得推薦。',N'NORMAL');
 SET @r_normal=CONVERT(INT,SCOPE_IDENTITY());
END;

DECLARE @r_hidden INT;
SELECT @r_hidden=review_id FROM dbo.game_reviews WITH(UPDLOCK,HOLDLOCK) WHERE member_id=@m_alice AND game_id=@g_live;

IF @r_hidden IS NULL BEGIN
 INSERT INTO dbo.game_reviews(member_id,game_id,rating,is_recommended,content,status) VALUES(@m_alice,@g_live,2,0,N'[QA26] 此為待複核的已隱藏評論。',N'HIDDEN');
 SET @r_hidden=CONVERT(INT,SCOPE_IDENTITY());
END;

DECLARE @r_deleted INT;
SELECT @r_deleted=review_id FROM dbo.game_reviews WITH(UPDLOCK,HOLDLOCK) WHERE member_id=@m_bob AND game_id=@g_gift;

IF @r_deleted IS NULL BEGIN
 INSERT INTO dbo.game_reviews(member_id,game_id,rating,is_recommended,content,status) VALUES(@m_bob,@g_gift,2,0,N'[QA26] 會員已刪除的評論。',N'DELETED');
 SET @r_deleted=CONVERT(INT,SCOPE_IDENTITY());
END;

IF NOT EXISTS(SELECT 1 FROM dbo.ugc_reports WHERE target_type=N'REVIEW' AND target_id=@r_normal AND reason=N'[QA26] pending 檢舉情境')
 INSERT INTO dbo.ugc_reports(target_type,target_id,reason,reporter_member_id,snapshot_text,status,admin_id,reviewed_at,review_comment,created_at) VALUES(N'REVIEW',@r_normal,N'[QA26] pending 檢舉情境',@m_alice,N'[QA26] 合作與探索體驗很好，值得推薦。',N'PENDING',NULL,NULL,NULL,DATEADD(DAY,-1,@now));

IF NOT EXISTS(SELECT 1 FROM dbo.ugc_reports WHERE target_type=N'REVIEW' AND target_id=@r_normal AND reason=N'[QA26] stale 檢舉情境')
 INSERT INTO dbo.ugc_reports(target_type,target_id,reason,reporter_member_id,snapshot_text,status,admin_id,reviewed_at,review_comment,created_at) VALUES(N'REVIEW',@r_normal,N'[QA26] stale 檢舉情境',@m_alice,N'[QA26] 已被修改的舊內容快照。',N'PENDING',NULL,NULL,NULL,DATEADD(DAY,-1,@now));

IF NOT EXISTS(SELECT 1 FROM dbo.ugc_reports WHERE target_type=N'REVIEW' AND target_id=@r_normal AND reason=N'[QA26] done 檢舉情境')
 INSERT INTO dbo.ugc_reports(target_type,target_id,reason,reporter_member_id,snapshot_text,status,admin_id,reviewed_at,review_comment,created_at) VALUES(N'REVIEW',@r_normal,N'[QA26] done 檢舉情境',@m_alice,N'[QA26] 合作與探索體驗很好，值得推薦。',N'DISMISSED',@a_reviewer,@now,N'QA26 已確認為正常遊戲心得',DATEADD(DAY,-1,@now));

IF NOT EXISTS(SELECT 1 FROM dbo.friendships WHERE requester_member_id=@m_member AND receiver_member_id=@m_alice)
 INSERT INTO dbo.friendships(requester_member_id,receiver_member_id,friend_status) VALUES(@m_member,@m_alice,N'ACCEPTED');

IF NOT EXISTS(SELECT 1 FROM dbo.friendships WHERE requester_member_id=@m_member AND receiver_member_id=@m_bob)
 INSERT INTO dbo.friendships(requester_member_id,receiver_member_id,friend_status) VALUES(@m_member,@m_bob,N'ACCEPTED');

IF NOT EXISTS(SELECT 1 FROM dbo.friendships WHERE requester_member_id=@m_alice AND receiver_member_id=@m_bob)
 INSERT INTO dbo.friendships(requester_member_id,receiver_member_id,friend_status) VALUES(@m_alice,@m_bob,N'PENDING');

DECLARE @froom INT;
SELECT @froom=friend_room_id FROM dbo.friend_rooms WITH(UPDLOCK,HOLDLOCK) WHERE room_name=N'[QA26] 好友群組';

IF @froom IS NULL BEGIN
 INSERT INTO dbo.friend_rooms(room_name,room_type,creator_member_id) VALUES(N'[QA26] 好友群組',N'GROUP',@m_member);
 SET @froom=CONVERT(INT,SCOPE_IDENTITY());
END;

IF NOT EXISTS(SELECT 1 FROM dbo.friend_room_members WHERE friend_room_id=@froom AND member_id=@m_member)
 INSERT INTO dbo.friend_room_members(friend_room_id,member_id,joined_at,left_at) VALUES(@froom,@m_member,DATEADD(DAY,-2,@now),NULL);

IF NOT EXISTS(SELECT 1 FROM dbo.friend_room_members WHERE friend_room_id=@froom AND member_id=@m_alice)
 INSERT INTO dbo.friend_room_members(friend_room_id,member_id,joined_at,left_at) VALUES(@froom,@m_alice,DATEADD(DAY,-2,@now),NULL);

IF NOT EXISTS(SELECT 1 FROM dbo.friend_room_members WHERE friend_room_id=@froom AND member_id=@m_bob)
 INSERT INTO dbo.friend_room_members(friend_room_id,member_id,joined_at,left_at) VALUES(@froom,@m_bob,DATEADD(DAY,-2,@now),DATEADD(DAY,-1,@now));

IF NOT EXISTS(SELECT 1 FROM dbo.friend_messages WHERE friend_room_id=@froom AND content=N'[QA26] 歡迎來到測試群組')
 INSERT INTO dbo.friend_messages(friend_room_id,content,member_id,message_type) VALUES(@froom,N'[QA26] 歡迎來到測試群組',@m_member,N'TEXT');

DECLARE @category INT;
SELECT @category=category_id FROM dbo.customer_service_categories WITH(UPDLOCK,HOLDLOCK) WHERE category_name=N'遊戲問題';

IF @category IS NULL BEGIN
 INSERT INTO dbo.customer_service_categories(category_name,description) VALUES(N'遊戲問題',N'遊戲安裝、啟動與內容問題');
 SET @category=CONVERT(INT,SCOPE_IDENTITY());
END;

DECLARE @t_waiting INT;
SELECT @t_waiting=ticket_id FROM dbo.customer_service_tickets WITH(UPDLOCK,HOLDLOCK) WHERE ticket_no=N'QA26-CS-waiting';

IF @t_waiting IS NULL BEGIN
 INSERT INTO dbo.customer_service_tickets(ticket_no,member_id,category_id,game_id,admin_id,subject,content,status,priority,created_at) VALUES(N'QA26-CS-waiting',@m_member,@category,@g_live,NULL,N'[QA26] waiting 客服案件',N'[QA26] 遊戲啟動時停在載入畫面，請協助確認。',N'OPEN',N'HIGH',DATEADD(DAY,-1,@now));
 SET @t_waiting=CONVERT(INT,SCOPE_IDENTITY());
END;

IF NOT EXISTS(SELECT 1 FROM dbo.customer_service_ticket_games WHERE ticket_id=@t_waiting AND game_id=@g_live)
 INSERT INTO dbo.customer_service_ticket_games(ticket_id,game_id,sort_order) VALUES(@t_waiting,@g_live,0);

IF NOT EXISTS(SELECT 1 FROM dbo.customer_service_ticket_games WHERE ticket_id=@t_waiting AND game_id=@g_gift)
 INSERT INTO dbo.customer_service_ticket_games(ticket_id,game_id,sort_order) VALUES(@t_waiting,@g_gift,1);

DECLARE @msg_waiting INT;
SELECT @msg_waiting=message_id FROM dbo.customer_service_messages WITH(UPDLOCK,HOLDLOCK) WHERE ticket_id=@t_waiting AND message_content=N'[QA26] 我已確認版本資訊，附上測試截圖。';

IF @msg_waiting IS NULL BEGIN
 INSERT INTO dbo.customer_service_messages(ticket_id,message_content,sender_member_id,sender_type) VALUES(@t_waiting,N'[QA26] 我已確認版本資訊，附上測試截圖。',@m_member,N'MEMBER');
 SET @msg_waiting=CONVERT(INT,SCOPE_IDENTITY());
END;

IF NOT EXISTS(SELECT 1 FROM dbo.customer_service_messages WHERE ticket_id=@t_waiting AND message_content=N'[QA26] 已收到問題，我們會協助處理。')
 INSERT INTO dbo.customer_service_messages(ticket_id,message_content,admin_id,sender_type) VALUES(@t_waiting,N'[QA26] 已收到問題，我們會協助處理。',@a_support,N'ADMIN');

DECLARE @t_mine INT;
SELECT @t_mine=ticket_id FROM dbo.customer_service_tickets WITH(UPDLOCK,HOLDLOCK) WHERE ticket_no=N'QA26-CS-mine';

IF @t_mine IS NULL BEGIN
 INSERT INTO dbo.customer_service_tickets(ticket_no,member_id,category_id,game_id,admin_id,subject,content,status,priority,created_at) VALUES(N'QA26-CS-mine',@m_alice,@category,@g_live,@a_support,N'[QA26] mine 客服案件',N'[QA26] 遊戲啟動時停在載入畫面，請協助確認。',N'IN_PROGRESS',N'URGENT',DATEADD(DAY,-1,@now));
 SET @t_mine=CONVERT(INT,SCOPE_IDENTITY());
END;

IF NOT EXISTS(SELECT 1 FROM dbo.customer_service_ticket_games WHERE ticket_id=@t_mine AND game_id=@g_live)
 INSERT INTO dbo.customer_service_ticket_games(ticket_id,game_id,sort_order) VALUES(@t_mine,@g_live,0);

IF NOT EXISTS(SELECT 1 FROM dbo.customer_service_ticket_games WHERE ticket_id=@t_mine AND game_id=@g_gift)
 INSERT INTO dbo.customer_service_ticket_games(ticket_id,game_id,sort_order) VALUES(@t_mine,@g_gift,1);

DECLARE @msg_mine INT;
SELECT @msg_mine=message_id FROM dbo.customer_service_messages WITH(UPDLOCK,HOLDLOCK) WHERE ticket_id=@t_mine AND message_content=N'[QA26] 我已確認版本資訊，附上測試截圖。';

IF @msg_mine IS NULL BEGIN
 INSERT INTO dbo.customer_service_messages(ticket_id,message_content,sender_member_id,sender_type) VALUES(@t_mine,N'[QA26] 我已確認版本資訊，附上測試截圖。',@m_alice,N'MEMBER');
 SET @msg_mine=CONVERT(INT,SCOPE_IDENTITY());
END;

IF NOT EXISTS(SELECT 1 FROM dbo.customer_service_messages WHERE ticket_id=@t_mine AND message_content=N'[QA26] 已收到問題，我們會協助處理。')
 INSERT INTO dbo.customer_service_messages(ticket_id,message_content,admin_id,sender_type) VALUES(@t_mine,N'[QA26] 已收到問題，我們會協助處理。',@a_support,N'ADMIN');

IF NOT EXISTS(SELECT 1 FROM dbo.customer_service_attachments WHERE message_id=@msg_mine AND file_name=N'qa26-example.png')
 INSERT INTO dbo.customer_service_attachments(message_id,file_name,content_type,size_bytes,width,height,image_data) VALUES(@msg_mine,N'qa26-example.png',N'image/png',@AttachmentBytes,320,180,@Attachment);

DECLARE @t_other INT;
SELECT @t_other=ticket_id FROM dbo.customer_service_tickets WITH(UPDLOCK,HOLDLOCK) WHERE ticket_no=N'QA26-CS-other';

IF @t_other IS NULL BEGIN
 INSERT INTO dbo.customer_service_tickets(ticket_no,member_id,category_id,game_id,admin_id,subject,content,status,priority,created_at) VALUES(N'QA26-CS-other',@m_bob,@category,@g_live,@a_staff_all,N'[QA26] other 客服案件',N'[QA26] 遊戲啟動時停在載入畫面，請協助確認。',N'IN_PROGRESS',N'NORMAL',DATEADD(DAY,-1,@now));
 SET @t_other=CONVERT(INT,SCOPE_IDENTITY());
END;

IF NOT EXISTS(SELECT 1 FROM dbo.customer_service_ticket_games WHERE ticket_id=@t_other AND game_id=@g_live)
 INSERT INTO dbo.customer_service_ticket_games(ticket_id,game_id,sort_order) VALUES(@t_other,@g_live,0);

IF NOT EXISTS(SELECT 1 FROM dbo.customer_service_ticket_games WHERE ticket_id=@t_other AND game_id=@g_gift)
 INSERT INTO dbo.customer_service_ticket_games(ticket_id,game_id,sort_order) VALUES(@t_other,@g_gift,1);

DECLARE @msg_other INT;
SELECT @msg_other=message_id FROM dbo.customer_service_messages WITH(UPDLOCK,HOLDLOCK) WHERE ticket_id=@t_other AND message_content=N'[QA26] 我已確認版本資訊，附上測試截圖。';

IF @msg_other IS NULL BEGIN
 INSERT INTO dbo.customer_service_messages(ticket_id,message_content,sender_member_id,sender_type) VALUES(@t_other,N'[QA26] 我已確認版本資訊，附上測試截圖。',@m_bob,N'MEMBER');
 SET @msg_other=CONVERT(INT,SCOPE_IDENTITY());
END;

IF NOT EXISTS(SELECT 1 FROM dbo.customer_service_messages WHERE ticket_id=@t_other AND message_content=N'[QA26] 已收到問題，我們會協助處理。')
 INSERT INTO dbo.customer_service_messages(ticket_id,message_content,admin_id,sender_type) VALUES(@t_other,N'[QA26] 已收到問題，我們會協助處理。',@a_staff_all,N'ADMIN');

DECLARE @t_closed INT;
SELECT @t_closed=ticket_id FROM dbo.customer_service_tickets WITH(UPDLOCK,HOLDLOCK) WHERE ticket_no=N'QA26-CS-closed';

IF @t_closed IS NULL BEGIN
 INSERT INTO dbo.customer_service_tickets(ticket_no,member_id,category_id,game_id,admin_id,subject,content,status,priority,created_at) VALUES(N'QA26-CS-closed',@m_empty,@category,@g_live,@a_support,N'[QA26] closed 客服案件',N'[QA26] 遊戲啟動時停在載入畫面，請協助確認。',N'CLOSED',N'LOW',DATEADD(DAY,-1,@now));
 SET @t_closed=CONVERT(INT,SCOPE_IDENTITY());
END;

IF NOT EXISTS(SELECT 1 FROM dbo.customer_service_ticket_games WHERE ticket_id=@t_closed AND game_id=@g_live)
 INSERT INTO dbo.customer_service_ticket_games(ticket_id,game_id,sort_order) VALUES(@t_closed,@g_live,0);

IF NOT EXISTS(SELECT 1 FROM dbo.customer_service_ticket_games WHERE ticket_id=@t_closed AND game_id=@g_gift)
 INSERT INTO dbo.customer_service_ticket_games(ticket_id,game_id,sort_order) VALUES(@t_closed,@g_gift,1);

DECLARE @msg_closed INT;
SELECT @msg_closed=message_id FROM dbo.customer_service_messages WITH(UPDLOCK,HOLDLOCK) WHERE ticket_id=@t_closed AND message_content=N'[QA26] 我已確認版本資訊，附上測試截圖。';

IF @msg_closed IS NULL BEGIN
 INSERT INTO dbo.customer_service_messages(ticket_id,message_content,sender_member_id,sender_type) VALUES(@t_closed,N'[QA26] 我已確認版本資訊，附上測試截圖。',@m_empty,N'MEMBER');
 SET @msg_closed=CONVERT(INT,SCOPE_IDENTITY());
END;

IF NOT EXISTS(SELECT 1 FROM dbo.customer_service_messages WHERE ticket_id=@t_closed AND message_content=N'[QA26] 已收到問題，我們會協助處理。')
 INSERT INTO dbo.customer_service_messages(ticket_id,message_content,admin_id,sender_type) VALUES(@t_closed,N'[QA26] 已收到問題，我們會協助處理。',@a_support,N'ADMIN');

DECLARE @chat_wait INT;
SELECT @chat_wait=support_room_id FROM dbo.chat_rooms WITH(UPDLOCK,HOLDLOCK) WHERE member_id=@m_member AND created_at=CONVERT(DATETIME2,'2026-01-01T00:00:04');

IF @chat_wait IS NULL BEGIN
 INSERT INTO dbo.chat_rooms(member_id,created_at,admin_id,status,ticket_id,closed_at) VALUES(@m_member,CONVERT(DATETIME2,'2026-01-01T00:00:04'),NULL,N'OPEN',NULL,NULL);
 SET @chat_wait=CONVERT(INT,SCOPE_IDENTITY());
END;

IF NOT EXISTS(SELECT 1 FROM dbo.chat_messages WHERE support_room_id=@chat_wait AND content=N'[QA26] 您好，我有遊戲相關問題。')
 INSERT INTO dbo.chat_messages(support_room_id,content,member_id,sender_type) VALUES(@chat_wait,N'[QA26] 您好，我有遊戲相關問題。',@m_member,N'MEMBER');

DECLARE @chat_mine INT;
SELECT @chat_mine=support_room_id FROM dbo.chat_rooms WITH(UPDLOCK,HOLDLOCK) WHERE member_id=@m_alice AND created_at=CONVERT(DATETIME2,'2026-01-01T00:00:04');

IF @chat_mine IS NULL BEGIN
 INSERT INTO dbo.chat_rooms(member_id,created_at,admin_id,status,ticket_id,closed_at) VALUES(@m_alice,CONVERT(DATETIME2,'2026-01-01T00:00:04'),@a_support,N'OPEN',NULL,NULL);
 SET @chat_mine=CONVERT(INT,SCOPE_IDENTITY());
END;

IF NOT EXISTS(SELECT 1 FROM dbo.chat_messages WHERE support_room_id=@chat_mine AND content=N'[QA26] 您好，我有遊戲相關問題。')
 INSERT INTO dbo.chat_messages(support_room_id,content,member_id,sender_type) VALUES(@chat_mine,N'[QA26] 您好，我有遊戲相關問題。',@m_alice,N'MEMBER');

IF NOT EXISTS(SELECT 1 FROM dbo.chat_messages WHERE support_room_id=@chat_mine AND content=N'[QA26] 您好，我來協助您。')
 INSERT INTO dbo.chat_messages(support_room_id,content,admin_id,sender_type) VALUES(@chat_mine,N'[QA26] 您好，我來協助您。',@a_support,N'ADMIN');

DECLARE @chat_other INT;
SELECT @chat_other=support_room_id FROM dbo.chat_rooms WITH(UPDLOCK,HOLDLOCK) WHERE member_id=@m_bob AND created_at=CONVERT(DATETIME2,'2026-01-01T00:00:05');

IF @chat_other IS NULL BEGIN
 INSERT INTO dbo.chat_rooms(member_id,created_at,admin_id,status,ticket_id,closed_at) VALUES(@m_bob,CONVERT(DATETIME2,'2026-01-01T00:00:05'),@a_staff_all,N'OPEN',NULL,NULL);
 SET @chat_other=CONVERT(INT,SCOPE_IDENTITY());
END;

IF NOT EXISTS(SELECT 1 FROM dbo.chat_messages WHERE support_room_id=@chat_other AND content=N'[QA26] 您好，我有遊戲相關問題。')
 INSERT INTO dbo.chat_messages(support_room_id,content,member_id,sender_type) VALUES(@chat_other,N'[QA26] 您好，我有遊戲相關問題。',@m_bob,N'MEMBER');

IF NOT EXISTS(SELECT 1 FROM dbo.chat_messages WHERE support_room_id=@chat_other AND content=N'[QA26] 您好，我來協助您。')
 INSERT INTO dbo.chat_messages(support_room_id,content,admin_id,sender_type) VALUES(@chat_other,N'[QA26] 您好，我來協助您。',@a_staff_all,N'ADMIN');

DECLARE @chat_converted INT;
SELECT @chat_converted=support_room_id FROM dbo.chat_rooms WITH(UPDLOCK,HOLDLOCK) WHERE member_id=@m_empty AND created_at=CONVERT(DATETIME2,'2026-01-01T00:00:09');

IF @chat_converted IS NULL BEGIN
 INSERT INTO dbo.chat_rooms(member_id,created_at,admin_id,status,ticket_id,closed_at) VALUES(@m_empty,CONVERT(DATETIME2,'2026-01-01T00:00:09'),@a_support,N'CLOSED',@t_closed,@now);
 SET @chat_converted=CONVERT(INT,SCOPE_IDENTITY());
END;

IF NOT EXISTS(SELECT 1 FROM dbo.chat_messages WHERE support_room_id=@chat_converted AND content=N'[QA26] 您好，我有遊戲相關問題。')
 INSERT INTO dbo.chat_messages(support_room_id,content,member_id,sender_type) VALUES(@chat_converted,N'[QA26] 您好，我有遊戲相關問題。',@m_empty,N'MEMBER');

IF NOT EXISTS(SELECT 1 FROM dbo.chat_messages WHERE support_room_id=@chat_converted AND content=N'[QA26] 您好，我來協助您。')
 INSERT INTO dbo.chat_messages(support_room_id,content,admin_id,sender_type) VALUES(@chat_converted,N'[QA26] 您好，我來協助您。',@a_support,N'ADMIN');

IF NOT EXISTS(SELECT 1 FROM dbo.member_login_logs WHERE member_id=@m_member AND ip_address=N'192.0.2.26' AND login_status=N'SUCCESS')
 INSERT INTO dbo.member_login_logs(member_id,ip_address,login_status,login_at) VALUES(@m_member,N'192.0.2.26',N'SUCCESS',DATEADD(DAY,-1,@now));

IF NOT EXISTS(SELECT 1 FROM dbo.member_login_logs WHERE member_id=@m_locked AND ip_address=N'192.0.2.26' AND login_status=N'LOCKED')
 INSERT INTO dbo.member_login_logs(member_id,ip_address,login_status,login_at) VALUES(@m_locked,N'192.0.2.26',N'LOCKED',DATEADD(DAY,-1,@now));

IF NOT EXISTS(SELECT 1 FROM dbo.member_login_logs WHERE member_id=@m_alice AND ip_address=N'192.0.2.26' AND login_status=N'FAILED')
 INSERT INTO dbo.member_login_logs(member_id,ip_address,login_status,login_at) VALUES(@m_alice,N'192.0.2.26',N'FAILED',DATEADD(DAY,-1,@now));

IF NOT EXISTS(SELECT 1 FROM dbo.notifications WHERE recipient_type=N'MEMBER' AND recipient_id=@m_member AND title=N'[QA26] 案件收到回覆')
 INSERT INTO dbo.notifications(recipient_type,recipient_id,title,event_type,body,target_path) VALUES(N'MEMBER',@m_member,N'[QA26] 案件收到回覆',N'TICKET_REPLY',N'[QA26] 測試通知，點擊查看對應頁面。','/member/support/'+CONVERT(VARCHAR(20),@t_waiting));

IF NOT EXISTS(SELECT 1 FROM dbo.notifications WHERE recipient_type=N'ADMIN' AND recipient_id=@a_support AND title=N'[QA26] 新聊天等待接手')
 INSERT INTO dbo.notifications(recipient_type,recipient_id,title,event_type,body,target_path) VALUES(N'ADMIN',@a_support,N'[QA26] 新聊天等待接手',N'NEW_CHAT',N'[QA26] 測試通知，點擊查看對應頁面。','/admin/chats?room='+CONVERT(VARCHAR(20),@chat_wait));

IF NOT EXISTS(SELECT 1 FROM dbo.notifications WHERE recipient_type=N'ADMIN' AND recipient_id=@a_reviewer AND title=N'[QA26] 遊戲等待上架審核')
 INSERT INTO dbo.notifications(recipient_type,recipient_id,title,event_type,body,target_path) VALUES(N'ADMIN',@a_reviewer,N'[QA26] 遊戲等待上架審核',N'GAME_SUBMITTED',N'[QA26] 測試通知，點擊查看對應頁面。',N'/admin/games?mode=pending');

IF NOT EXISTS(SELECT 1 FROM dbo.ai_game_recommendations WHERE member_id=@m_member AND game_id=@g_gift)
 INSERT INTO dbo.ai_game_recommendations(member_id,game_id,recommendation_score,reason,model_version) VALUES(@m_member,@g_gift,0.85,N'[QA26] 依冒險與合作偏好產生的測試推薦',N'QA26-fixture');

IF NOT EXISTS(SELECT 1 FROM dbo.ai_game_recommendations WHERE member_id=@m_member AND game_id=@g_soon)
 INSERT INTO dbo.ai_game_recommendations(member_id,game_id,recommendation_score,reason,model_version) VALUES(@m_member,@g_soon,0.85,N'[QA26] 依冒險與合作偏好產生的測試推薦',N'QA26-fixture');

IF NOT EXISTS(SELECT 1 FROM dbo.ai_game_recommendations WHERE member_id=@m_member AND game_id=@g_free)
 INSERT INTO dbo.ai_game_recommendations(member_id,game_id,recommendation_score,reason,model_version) VALUES(@m_member,@g_free,0.85,N'[QA26] 依冒險與合作偏好產生的測試推薦',N'QA26-fixture');

IF NOT EXISTS(SELECT 1 FROM dbo.ai_recommendation_refresh_status WHERE member_id=@m_member)
 INSERT INTO dbo.ai_recommendation_refresh_status(member_id,refresh_status,last_attempt_at,last_success_at,next_refresh_at) VALUES(@m_member,N'SUCCESS',@now,@now,DATEADD(DAY,7,@now));

IF NOT EXISTS(SELECT 1 FROM dbo.member_game_view_stats WHERE member_id=@m_member AND game_id=@g_live)
 INSERT INTO dbo.member_game_view_stats(member_id,game_id,view_count) VALUES(@m_member,@g_live,5);

IF NOT EXISTS(SELECT 1 FROM dbo.member_tag_search_stats WHERE member_id=@m_member AND tag_id=@tag0)
 INSERT INTO dbo.member_tag_search_stats(member_id,tag_id,search_count) VALUES(@m_member,@tag0,3);

IF NOT EXISTS(SELECT 1 FROM dbo.play_records WHERE member_id=@m_member AND game_id=@g_live AND started_at=CONVERT(DATETIME2,'2026-10-01T12:00:00'))
 INSERT INTO dbo.play_records(member_id,game_id,started_at,ended_at,play_seconds) VALUES(@m_member,@g_live,CONVERT(DATETIME2,'2026-10-01T12:00:00'),CONVERT(DATETIME2,'2026-10-01T13:00:00'),3600);

DECLARE @product INT;
SELECT @product=product_id FROM dbo.point_products WITH(UPDLOCK,HOLDLOCK) WHERE product_name=N'[QA26] 測試點數商品';

IF @product IS NULL BEGIN
 INSERT INTO dbo.point_products(product_name,description,point_price,status) VALUES(N'[QA26] 測試點數商品',N'僅供點數資料展示，非真實兌換品',100,N'ACTIVE');
 SET @product=CONVERT(INT,SCOPE_IDENTITY());
END;

IF NOT EXISTS(SELECT 1 FROM dbo.point_exchange_orders WHERE member_id=@m_member AND product_id=@product)
 INSERT INTO dbo.point_exchange_orders(member_id,product_id,points_spent,exchange_status) VALUES(@m_member,@product,100,N'COMPLETED');

IF NOT EXISTS(SELECT 1 FROM dbo.member_point_logs WHERE member_id=@m_member AND change_reason=N'QA26_INIT')
 INSERT INTO dbo.member_point_logs(member_id,change_reason,points_change,balance) VALUES(@m_member,N'QA26_INIT',1100,1100);

IF NOT EXISTS(SELECT 1 FROM dbo.member_point_logs WHERE member_id=@m_member AND change_reason=N'QA26_EXCHANGE')
 INSERT INTO dbo.member_point_logs(member_id,change_reason,points_change,balance) VALUES(@m_member,N'QA26_EXCHANGE',-100,1000);

COMMIT TRANSACTION;
END TRY
BEGIN CATCH
 IF @@TRANCOUNT>0 ROLLBACK;
 THROW;
END CATCH;
GO
SELECT admin_account,admin_name,status FROM dbo.admin WHERE admin_account LIKE 'qa26[_]%';
SELECT username,email,is_publisher,locked_until FROM dbo.members WHERE username LIKE 'qa26[_]%';
