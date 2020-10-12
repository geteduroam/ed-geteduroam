import { Component } from '@angular/core';
import {Events, NavController, NavParams} from 'ionic-angular';
import { LoadingProvider } from '../../providers/loading/loading';
import { ProfileModel } from '../../shared/models/profile-model';
import { GeteduroamServices } from '../../providers/geteduroam-services/geteduroam-services';
import { oAuthModel } from '../../shared/models/oauth-model';
import { HTTP } from '@ionic-native/http/ngx';
import {BasePage} from "../basePage";
import {DictionaryServiceProvider} from "../../providers/dictionary-service/dictionary-service-provider.service";
import {GlobalProvider} from "../../providers/global/global";
import {ProviderInfo} from "../../shared/entities/providerInfo";
import {ErrorHandlerProvider} from "../../providers/error-handler/error-handler";
import {OauthConfProvider} from "../../providers/oauth-conf/oauth-conf";

declare var window: any;

@Component({
  selector: 'page-oauthFlow',
  templateUrl: 'oauthFlow.html',
})

export class OauthFlow extends BasePage{

  showAll: boolean = false;

  /**
   * Model Profile
   */
  profile: ProfileModel;

  /**
   * This provide the url to get a token
   */
  tokenURl: any;

  /**
   * Provide info from a certificate
   */
  providerInfo: ProviderInfo = new ProviderInfo();

  constructor(private http: HTTP, public navCtrl: NavController, public navParams: NavParams, protected loading: LoadingProvider,
              private getEduroamServices: GeteduroamServices, protected dictionary: DictionaryServiceProvider, protected event: Events,
              protected global: GlobalProvider, private errorHandler: ErrorHandlerProvider)  {
    super(loading, dictionary, event, global);

  }

  /**
   * Method to open browser and initialize the oAuth flow
   * [Api Documentation]{@link https://github.com/Uninett/lets-wifi/blob/master/API.md#authorization-endpoint}
   * @param oAuth is an object to request the authorized endpoint
   * @param oauth2Options: oAuthModel
   * @param token_endpoint: url token
   */
  buildFlowAuth(oAuth, oauth2Options: oAuthModel, token_endpoint) {
    let urlToken;

    let target = !!this.global.isAndroid() ? "": "_blank";
    // Initialized browser inside app
    let browserRef = window.cordova.InAppBrowser.open(oAuth.uri, target, "location=yes,clearsessioncache=no,clearcache=no,hidespinner=yes");

    browserRef.addEventListener("loadstart", async (event) => {

      // Extract code and state to build authorized request
      if (event.url.indexOf(oauth2Options.redirectUrl) === 0 && event.url.indexOf("error") === -1 ) {
        let urlData = event.url.split('code=')[1];
        let arrayData = urlData.split('&state=');
        let code = arrayData[0];
        let state = arrayData[1];

        if (state !== undefined && code !== undefined) {
          // Header to get token
          urlToken = `client_id=${oauth2Options.client_id}&grant_type=authorization_code&code=${code}&code_verifier=${oAuth.codeVerifier}&redirect_uri=${oauth2Options.redirectUrl}`;
          /*urlToken = {
            'client_id': oauth2Options.client_id,
            'grant_type': 'authorization_code',
            'code': code,
            'code_verifier': oAuth.codeVerifier,
            'redirect_uri': oauth2Options.redirectUrl
          };*/
          await browserRef.close();
          await this.getToken(urlToken);
        }
      } else if (event.url.indexOf(oauth2Options.redirectUrl) === 0 && event.url.includes('?error')) {
        browserRef.close();
        this.closeEventBrowser(event.url.includes('?error'));
      }
    });

    browserRef.addEventListener("exit", (event) => {
      this.closeEventBrowser();
    })
  }

  closeEventBrowser(error?: boolean) {
    this.loading.create();
    if (!!error) {
      this.errorHandler.handleError(this.dictionary.getTranslation('error', 'invalid-oauth'), false, '', '', true);
    }
    this.loading.dismiss();
  }

  /**
   * Method to create request to token
   * [Api Documentation]{@link https://github.com/Uninett/lets-wifi/blob/master/API.md#token-endpoint}
   * @param res url to get token
   */
  async getToken(res) {
    this.showSpinner();

    const opts: any = {};
    opts.method = 'post';
    opts.data = res;
    opts.serializer = 'urlencoded';
    opts.headers = {
      'content-type': 'application/x-www-form-urlencoded'
    };
    opts.responseType = 'json';
    this.http.setDataSerializer('utf8');
    const response = await this.http.post(this.profile.token_endpoint, res, {'Content-Type':'application/x-www-form-urlencoded'});//await this.http.sendRequest(this.profile.token_endpoint, opts);
    console.log(response.data);
    this.tokenURl = JSON.parse(response.data);
    this.profile.token = this.tokenURl.access_token;

    const validProfile:boolean = await this.getEduroamServices.eapValidation(this.profile);
    const oauthConf: OauthConfProvider = new OauthConfProvider(this.global, this.getEduroamServices, this.loading, this.errorHandler, this.dictionary, this.navCtrl);
    this.providerInfo = this.global.getProviderInfo();
    await oauthConf.manageProfileValidation(validProfile, this.providerInfo);

  }

  /**
   * This method create object to get data from
   * the oAuth flow and generate certificates
   */
  async getData() {
    const oauth2Options: oAuthModel = this.oAuthModel();

    let oAuth = await this.getEduroamServices.generateOAuthFlow(oauth2Options);

    this.buildFlowAuth(oAuth, oauth2Options, this.profile.token_endpoint);
  }

  /**
   * Lifecycle: Method executed when the class did enter, usually when swipe back from the next page
   */
  async ionViewDidEnter() {
    this.loading.createAndPresent();
    this.profile = this.navParams.get('profile');
    await this.getData();
    this.loading.dismiss();
    this.showAll = true;
  }

  /**
   * Method to build spinner loading
   */
  showSpinner() {
    this.loading.createAndPresent();
  }

  /**
   * Provided oAuth Model by the authorization-endpoint
   * [Api Documentation]{@link https://github.com/Uninett/lets-wifi/blob/master/API.md#token-endpoint}
   */
  private oAuthModel() {
    return {
      client_id: this.global.getClientId(),
      oAuthUrl: this.profile.authorization_endpoint,
      type: 'code',
      redirectUrl: 'http://127.0.0.1:8080/',
      pkce: true,
      scope: 'eap-metadata',
    };
  }
}
