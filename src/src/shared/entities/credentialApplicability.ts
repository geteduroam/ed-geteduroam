import {IEEE80211} from "./iEEE80211";
import {BaseJson} from "./baseJson";
import {isArray, isObject} from "ionic-angular/util/util";
import {GlobalProvider} from "../../providers/global/global";


export class CredentialApplicability extends BaseJson{
  /**
   * The IEEE80211
   */
  iEEE80211 : IEEE80211[];

  constructor(private global: GlobalProvider) {
    super();
  }

  /**
   * Method which fills the CredentialApplicability by filling every IEEE80211
   * This method updates the property [iEEE80211]{@link #iEEE80211}
   * @param {any} jsonAux json from which to retrieve the info.
   */
  fillEntity(jsonAux: any):boolean{
    let returnValue: boolean = true;
    returnValue = returnValue && this.fillProperties(jsonAux);
    //this.ca = new Ca();
    // returnValue = returnValue && this.assignCaArray(this.ca, 'ca', jsonAux, 'CA', true);
    // this.serverID = this.getSingleProperty(jsonAux, 'ServerID', false);
    console.log('CredentialApplicability', this);
    return returnValue;
  }

  protected fillProperties<T extends BaseJson> (propertyValue: any):boolean {
    let returnValue: boolean = true;
    console.log(propertyValue);
    try {
      this.iEEE80211 = propertyValue['IEEE80211'];
      let i = 0;
      let enc = false;
      while (i < this.iEEE80211.length && !enc) {
        if ( 'SSID' in this.iEEE80211[i] ) {
          this.global.setSsid(this.iEEE80211[i]['SSID'][0]);
          enc = true;
        } else {
          i++;
        }
      }
      if (!enc) {
        returnValue = false;
      }
    } catch (e) {
      returnValue = false;
    }
    return returnValue;
  }
}
