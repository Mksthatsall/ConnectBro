/* Creates an idempotent sample community through the real local backend APIs. */
(function () {
  const password = 'ConnectBroDemo42!';
  const marker = '[ConnectBro sample]';
  const samples = [
    {name:'Avery Morgan',email:'avery.demo@connectbro.example',headline:'Product strategist | Turning customer insight into clear roadmaps',location:'Toronto, Canada',about:'I connect product discovery to measurable outcomes and enjoy making complex work easier to explain.',skills:'Product strategy, Data storytelling, User research',interests:'Responsible AI, Early-stage teams',goals:'Finding a mentor, Building useful AI products, Collaborating across disciplines'},
    {name:'Blair Chen',email:'blair.demo@connectbro.example',headline:'Staff engineer | Distributed systems and developer experience',location:'Vancouver, Canada',about:'Building reliable systems and helping teams ship with confidence.',skills:'Java, Distributed systems, Platform engineering',interests:'Open source, Responsible AI',goals:'Mentoring engineers, Sharing platform practices, Collaborating on AI tools'},
    {name:'Casey Rivera',email:'casey.demo@connectbro.example',headline:'Product manager | Developer tools and cloud platforms',location:'Austin, United States',about:'I work across engineering, design, and customer teams to make useful tools.',skills:'Product strategy, Java, Developer tools',interests:'Responsible AI, Accessibility',goals:'Finding a technical mentor, Building responsible AI products, Growing as a product leader'},
    {name:'Devon Patel',email:'devon.demo@connectbro.example',headline:'Data engineer | Building practical analytics foundations',location:'London, United Kingdom',about:'Interested in trustworthy data systems and clear ways to share what the numbers mean.',skills:'Data engineering, Data storytelling, SQL',interests:'Climate tech, Responsible AI',goals:'Mentoring data teams, Collaborating on climate analytics, Leading a data platform'},
    {name:'Ellis Okafor',email:'ellis.demo@connectbro.example',headline:'UX researcher | Making technical products easier to use',location:'Dublin, Ireland',about:'I help teams understand people and turn research into better product decisions.',skills:'User research, Accessibility, Product discovery',interests:'Public interest technology, Climate tech',goals:'Finding a research mentor, Improving accessible products, Collaborating with climate teams'}
  ];

  const pause = ms => new Promise(resolve => setTimeout(resolve, ms));
  async function call(root, token, path, options = {}) {
    const headers = new Headers(options.headers || {});
    if (token) headers.set('Authorization', `Bearer ${token}`);
    if (options.body) headers.set('Content-Type','application/json');
    const response = await fetch(`${root}${path}`, {...options,headers});
    const text = await response.text();
    let value = null;
    if (text) { try { value = JSON.parse(text); } catch { value = text; } }
    if (!response.ok) throw new Error(value?.message || value?.error || (typeof value==='string'?value:`Request failed (${response.status})`));
    return value;
  }

  async function account(root, sample, progress) {
    progress(`Preparing ${sample.name}…`);
    try {
      await call(root,null,'/users/auth/signup',{method:'POST',body:JSON.stringify({name:sample.name,email:sample.email,password})});
    } catch (error) {
      // Existing demo accounts are expected when setup is repeated. Login below verifies ownership.
      if (!/exist|already|duplicate|conflict/i.test(error.message)) throw error;
    }
    const token = await call(root,null,'/users/auth/login',{method:'POST',body:JSON.stringify({email:sample.email,password})});
    const me = await call(root,token,'/users/me');
    await call(root,token,'/users/me',{method:'PUT',body:JSON.stringify({headline:sample.headline,about:sample.about,location:sample.location,profilePicture:'',skills:sample.skills,interests:sample.interests,goals:sample.goals})});
    return {...sample,id:Number(me.id),token};
  }

  function pairMatches(row,a,b) {
    return (Number(row.requesterUserId)===a&&Number(row.receiverUserId)===b) || (Number(row.requesterUserId)===b&&Number(row.receiverUserId)===a);
  }

  async function ensureConnection(root,a,b,users,progress) {
    const accepted = await call(root,users[a].token,'/connections/core/connections');
    if ((accepted||[]).some(row=>Number(row.lowUserId)===Math.min(users[a].id,users[b].id)&&Number(row.highUserId)===Math.max(users[a].id,users[b].id))) return;
    let [outA,inA,outB,inB] = await Promise.all([
      call(root,users[a].token,'/connections/core/requests/outgoing'),call(root,users[a].token,'/connections/core/requests/incoming'),
      call(root,users[b].token,'/connections/core/requests/outgoing'),call(root,users[b].token,'/connections/core/requests/incoming')
    ]);
    let request = [...(outA||[]),...(inA||[]),...(outB||[]),...(inB||[])].find(row=>pairMatches(row,users[a].id,users[b].id));
    if (!request) {
      progress(`Connecting ${users[a].name.split(' ')[0]} and ${users[b].name.split(' ')[0]}…`);
      try { request = await call(root,users[a].token,`/connections/core/requests/${users[b].id}`,{method:'POST'}); }
      catch (error) {
        if (!/pending|already connected/i.test(error.message)) throw error;
        [outA,inA,outB,inB] = await Promise.all([
          call(root,users[a].token,'/connections/core/requests/outgoing'),call(root,users[a].token,'/connections/core/requests/incoming'),
          call(root,users[b].token,'/connections/core/requests/outgoing'),call(root,users[b].token,'/connections/core/requests/incoming')
        ]);
        request=[...(outA||[]),...(inA||[]),...(outB||[]),...(inB||[])].find(row=>pairMatches(row,users[a].id,users[b].id));
      }
    }
    if (!request) return;
    const receiver = users.find(user=>user.id===Number(request.receiverUserId));
    await call(root,receiver.token,`/connections/core/requests/${request.id}/accept`,{method:'POST'});
  }

  async function ensurePost(root,user,body) {
    const posts = await call(root,user.token,`/posts/core/users/${user.id}/allPosts`);
    const post = (posts||[]).find(item=>item.content?.includes(marker));
    if (post) return post;
    return call(root,user.token,'/posts/core',{method:'POST',body:JSON.stringify({content:`${marker} ${body}`})});
  }

  async function run(root, progress = () => {}) {
    const users=[];
    for (const sample of samples) users.push(await account(root,sample,progress));

    for (const [a,b] of [[0,1],[1,2],[1,3],[2,3],[2,4]]) await ensureConnection(root,a,b,users,progress);
    progress('Syncing accepted connections to the recommendation graph…');
    await pause(6000);

    const postText = [
      'A clear product question can save a team a month of building the wrong thing. What is one discovery habit that has helped your team?',
      'We made a small improvement to our service dashboards: every alert now includes the next action and the team that owns it. The change has made handoffs much calmer.',
      'A useful roadmap is a set of choices, not a promise that every idea will ship. We have started sharing the customer evidence behind each choice.',
      'A data point becomes more useful when people understand where it came from and what decision it can support. I am collecting examples of teams doing this well.',
      'A research note is only valuable if the team can find it when making a decision. We are experimenting with short, searchable insight summaries.'
    ];
    const posts=[];
    for (let i=0;i<users.length;i++) { progress(`Adding sample posts (${i+1}/${users.length})…`); posts.push(await ensurePost(root,users[i],postText[i])); }

    const engagement=await call(root,users[0].token,`/posts/likes/${posts[1].id}`);
    if (!engagement.likedByMe) await call(root,users[0].token,`/posts/likes/${posts[1].id}`,{method:'POST'});
    const comments=await call(root,users[0].token,`/posts/core/${posts[1].id}/comments`);
    if (!(comments||[]).some(comment=>comment.content?.includes(marker))) {
      await call(root,users[0].token,`/posts/core/${posts[1].id}/comments`,{method:'POST',body:JSON.stringify({content:`${marker} Adding the action and owner to alerts made a real difference for our team too.`})});
    }

    progress('Checking recommendations and finishing up…');
    const started=Date.now();
    while (Date.now()-started<20000) {
      const recs=await call(root,users[0].token,'/connections/core/recommendations?page=0&size=20');
      if ((recs.items||[]).length>=2) break;
      await pause(2000);
    }
    return users[0].token;
  }

  window.ConnectBroDemo = {run, email:samples[0].email, password};
})();
